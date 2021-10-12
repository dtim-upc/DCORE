package dcer.distribution

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import dcer.actors.EngineManager.MatchGroupingId
import dcer.actors.{EngineManager, Worker}
import dcer.data.{Configuration, DistributionStrategy, Match}
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.collection.JavaConverters._

// TODO (optionally)
// Add workers dynamically
// This would affect the distribution strategies e.g. rebalance of work.

sealed trait Distributor {
  val ctx: ActorContext[EngineManager.Event]
  val workers: Set[ActorRef[Worker.Command]]
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
      workers: Set[ActorRef[Worker.Command]],
      predicate: Predicate
  ): Distributor =
    distributionStrategy match {
      case DistributionStrategy.Sequential =>
        Sequential(ctx, workers, predicate)
      case DistributionStrategy.RoundRobin =>
        RoundRobin(ctx, workers, predicate)
    }

  def fromConfig(config: Configuration.Parser)(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]]
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
      workers: Set[ActorRef[Worker.Command]],
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
        theWorker ! Worker.Process(id, Match(coreMatch), predicate, ctx.self)
      }
    }
  }

  private case class RoundRobin(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Distributor {

    var lastIndex: Int = 0

    override def distributeInner(
        id: MatchGroupingId,
        matchGrouping: MatchGrouping
    ): Unit = {
      val nWorkers = workers.size
      val workersMap = workers.zipWithIndex.map(_.swap).toMap // from 0 to n-1
      matchGrouping.iterator().asScala.foreach { coreMatch =>
        val worker = workersMap(lastIndex)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        worker ! Worker.Process(id, Match(coreMatch), predicate, ctx.self)
        lastIndex = (lastIndex + 1) % nWorkers
      }
    }
  }
}
