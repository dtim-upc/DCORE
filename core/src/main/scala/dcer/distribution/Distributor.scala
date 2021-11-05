package dcer.distribution

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import dcer.actors.EngineManager.MatchGroupingId
import dcer.actors.{EngineManager, Worker}
import dcer.data.Match.MaximalMatch
import dcer.data.{Configuration, DistributionStrategy, Match}
import dcer.distribution.Blueprint.NumberOfMatches
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.collection.mutable.ListBuffer

sealed trait Distributor {
  val ctx: ActorContext[EngineManager.Event]
  val workers: Array[ActorRef[Worker.Command]]
  val predicate: Predicate

  def distributeInner(
      id: MatchGroupingId,
      matchGrouping: MatchGrouping
  ): Map[ActorRef[Worker.Command], Long]

  // Returns the #matches assigned to each worker.
  def distribute(
      id: MatchGroupingId,
      matchGrouping: MatchGrouping
  ): Map[ActorRef[Worker.Command], Long] = {
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
    ): Map[ActorRef[Worker.Command], Long] = {
      var nMatches = 0L
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        nMatches += 1
        ctx.log.debug(s"Sending match to worker: ${theWorker.path}")
        theWorker ! Worker
          .ProcessMatch(id, Match(coreMatch), predicate, ctx.self)
      }
      Map(theWorker -> nMatches)
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
    ): Map[ActorRef[Worker.Command], Long] = {
      val nWorkers = workers.length
      val nMatches = Array.fill(nWorkers)(0L)
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val worker = workers(lastIndex)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        worker ! Worker.ProcessMatch(id, Match(coreMatch), predicate, ctx.self)
        nMatches(lastIndex) += 1
        lastIndex = (lastIndex + 1) % nWorkers
      }
      workers.zip(nMatches).toMap
    }
  }

  private case class RoundRobinWeighted(
      ctx: ActorContext[EngineManager.Event],
      workers: Array[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    // The first element is the load.
    // The second element the index in the array.
    // This allows to efficiently implement
    val load: Array[Long] = Array.fill(workers.length)(0L)
    val oneToN: immutable.Seq[Int] = 1 until load.length

    private def minLoadIndex(): Int = {
      // Pre-condition: Length >= 1
      var min = load.head
      var minIndex = 0
      oneToN.foreach { i =>
        val e = load(i)
        if (e < min) {
          min = e
          minIndex = i
        }
      }
      minIndex
    }

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Map[ActorRef[Worker.Command], Long] = {
      val nMatches = Array.fill(workers.length)(0L)

      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val index = minLoadIndex()
        val worker = workers(index)

        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        val m = Match(coreMatch)
        worker ! Worker.ProcessMatch(id, m, predicate, ctx.self)

        nMatches(index) += 1
        load(index) += Match.weight(m)
      }

      workers.zip(nMatches).toMap
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
    ): Map[ActorRef[Worker.Command], Long] = {
      val nMatches = Array.fill(workers.length)(0L)

      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val index1 = rng.nextInt(workers.length)
        val index2 = rng.nextInt(workers.length)
        val index = if (load(index1) <= load(index2)) index1 else index2

        val worker = workers(index)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        val m = Match(coreMatch)
        worker ! Worker.ProcessMatch(id, m, predicate, ctx.self)

        nMatches(index) += 1
        load(index) += Match.weight(m)
      }

      workers.zip(nMatches).toMap
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
    ): Map[ActorRef[Worker.Command], Long] = {

      // (1) Blueprints
      val buffer: ListBuffer[(MaximalMatch, Blueprint, NumberOfMatches)] =
        ListBuffer()
      matchGrouping.forEach { coreMaximalMatch =>
        val maximalMatch = Match(coreMaximalMatch)
        buffer ++= Blueprint.fromMaximalMatch(maximalMatch).map {
          case (b, matches) => (maximalMatch, b, matches)
        }
      }
      val blueprints = buffer.toArray

      // (2) Load-balancing problem
      //
      // Our first implementation is based on a naive greedy algorithm.
      val k = workers.length
      val n = blueprints.length
      val load: Array[Long] = Array.fill(k)(0)
      val nMatches = Array.fill(k)(0L) // same as load (for now)
      val sortedBlueprints = blueprints.sortBy(_._3)(Ordering.Long.reverse)

      def assign(blueprint_index: Int, worker_index: Int): Unit = {
        val worker = workers(worker_index)
        val (mm, bp, cost) = sortedBlueprints(blueprint_index)
        ctx.log.debug(
          s"Sending ${bp.pretty} to worker: ${worker.path}"
        )
        worker ! Worker.ProcessMaximalMatch(id, mm, bp, predicate, ctx.self)
        load(worker_index) += cost
        nMatches(worker_index) += cost
      }

      // The first k can be assigned without checking the load
      (0 until Math.min(n, k)).foreach(blueprint_index =>
        assign(blueprint_index, blueprint_index)
      )

      (k until n).foreach { blueprint_index =>
        // arg min can be computed faster
        val worker_min_load = load.zipWithIndex.minBy(_._1)._2
        assign(blueprint_index, worker_min_load)
      }

      workers.zip(nMatches).toMap
    }
  }
}
