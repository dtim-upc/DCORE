package dcer.distribution

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import dcer.Binomial
import dcer.actors.EngineManager.MatchGroupingId
import dcer.actors.{EngineManager, Worker}
import dcer.data.Match.MaximalMatch
import dcer.data.{Configuration, DistributionStrategy, Match}
import dcer.distribution.Blueprint.EventTypeSeqSize
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

sealed trait Distributor {
  val ctx: ActorContext[EngineManager.Event]
  val workers: Array[ActorRef[Worker.Command]]
  val predicate: Predicate
  def distributeInner(
      id: MatchGroupingId,
      matchGrouping: MatchGrouping
  ): Unit
  def distribute(id: MatchGroupingId, matchGrouping: MatchGrouping): Unit = {
    ctx.log.info(s"Distributing ${matchGrouping.size()} matches")
    distributeInner(id, matchGrouping)
  }
}

object Distributor {

  def apply(
      distributionStrategy: DistributionStrategy,
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ): Distributor =
    distributionStrategy match {
      case DistributionStrategy.Sequential =>
        Sequential(ctx, workers, predicate)
      case DistributionStrategy.RoundRobin =>
        RoundRobin(ctx, workers, predicate)
      case DistributionStrategy.RoundRobinWeighted =>
        RoundRobinWeighted(ctx, workers, predicate)
      case DistributionStrategy.PowerOfTwoChoices =>
        PowerOfTwoChoices(ctx, workers, predicate)
      case DistributionStrategy.MaximalMatchesEnumeration =>
        MaximalMatchesEnumeration(ctx, workers, predicate)
    }

  def fromConfig(config: Configuration.Parser)(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]]
  ): Distributor = {
    val predicate: Predicate =
      config.getValueOrThrow(Configuration.SecondOrderPredicateKey)(
        Predicate.parse
      )

    val strategy: DistributionStrategy =
      config.getValueOrThrow(Configuration.DistributionStrategyKey)(
        DistributionStrategy.parse
      )

    Distributor(strategy, ctx, workers, predicate)
  }

  private case class Sequential(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    // In sequential distribution, there is only one actor processing all matches.
    // We pick one arbitrary.
    val theWorker: ActorRef[Worker.Command] = workers.head

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        ctx.log.debug(s"Sending match to worker: ${theWorker.path}")
        theWorker ! Worker
          .ProcessMatch(id, Match(coreMatch), predicate, ctx.self)
      }
    }
  }

  private case class RoundRobin(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    var lastIndex: Int = 0

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {
      val nWorkers = workers.length
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val worker = workers(lastIndex)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        worker ! Worker.ProcessMatch(id, Match(coreMatch), predicate, ctx.self)
        lastIndex = (lastIndex + 1) % nWorkers
      }
    }
  }

  private case class RoundRobinWeighted(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    val load: Array[(Long, Int)] = Array.fill(workers.length)(0L).zipWithIndex

    private def minLoadIndex(): Int =
      load.minBy(_._1)._2

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val index = minLoadIndex()

        val worker = workers(index)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        val m = Match(coreMatch)
        worker ! Worker.ProcessMatch(id, m, predicate, ctx.self)

        load(index) =
          load(index) match { case (w, i) => (w + Match.weight(m), i) }
      }
    }
  }

  private case class PowerOfTwoChoices(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {
    val rng: scala.util.Random = scala.util.Random
    val load: Array[Long] = Array.fill(workers.length)(0)

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val index1 = rng.nextInt(workers.length)
        val index2 = rng.nextInt(workers.length)
        val index = if (load(index1) <= load(index2)) index1 else index2

        val worker = workers(index)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        val m = Match(coreMatch)
        worker ! Worker.ProcessMatch(id, m, predicate, ctx.self)

        load(index) += Match.weight(m)
      }
    }
  }

  private case class MaximalMatchesEnumeration(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    type Cost = Long

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {

      // (1) Blueprints
      val buffer: ListBuffer[(MaximalMatch, Blueprint, EventTypeSeqSize)] =
        ListBuffer()

      matchGrouping.forEach { coreMaximalMatch =>
        val maximalMatch = Match(coreMaximalMatch)
        val (blueprints, eventTypeSize) =
          Blueprint.fromMaximalMatch(maximalMatch)
        buffer ++= blueprints.map((maximalMatch, _, eventTypeSize))
      }

      // (2) Blueprints' cost for load balancing
      val blueprints: Array[(MaximalMatch, Blueprint, Cost)] =
        buffer.toArray.map { case (maximalMatch, blueprint, eventTypeSeqSize) =>
          val cost = blueprint.value
            .zip(eventTypeSeqSize)
            .map { case (k, n) =>
              Binomial.binomialUnsafe(k, n)
            }
            .product
          (maximalMatch, blueprint, cost)
        }

      // (3) Load-balancing problem
      //
      // Our first implementation is based on a naive greedy algorithm.
      val k = workers.length
      val n = blueprints.length
      val load: Array[Long] = Array.fill(k)(0)
      val sortedBlueprints = blueprints.sortBy(_._3)(Ordering.Long.reverse)

      def assign(blueprint_index: Int, worker_index: Int): Unit = {
        val worker = workers(worker_index)
        val (mm, bp, cost) = sortedBlueprints(blueprint_index)
        ctx.log.debug(
          s"Sending ${bp.pretty} to worker: ${worker.path}"
        )
        worker ! Worker.ProcessMaximalMatch(id, mm, bp, predicate, ctx.self)
        load(worker_index) += cost
      }

      // The first k can be assigned without checking the load
      (0 until Math.min(n, k)).foreach(blueprint_index =>
        assign(blueprint_index, blueprint_index)
      )

      (k until n).foreach { blueprint_index =>
        val worker_min_load = load.zipWithIndex.minBy(_._1)._2
        assign(blueprint_index, worker_min_load)
      }
    }
  }
}
