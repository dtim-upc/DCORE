package dcer.distribution

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import dcer.actors.{EngineManager, Worker}
import dcer.data.Match
import edu.puc.core.execution.structures.output.MatchGrouping
import scala.collection.JavaConverters._

// TODO (optionally)
// Add workers dynamically
// This would affect the distribution strategies e.g. rebalance of work.

sealed trait DistributionStrategy {
  val ctx: ActorContext[EngineManager.Event]
  val workers: Set[ActorRef[Worker.Command]]
  val predicate: SecondOrderPredicate
  def distribute(matchGroup: MatchGrouping): Unit
}

object DistributionStrategy {

  case class Sequential(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: SecondOrderPredicate
  ) extends DistributionStrategy {
    override def distribute(matchGroup: MatchGrouping): Unit = {
      throw new RuntimeException("Not implemented")
    }
  }

  case class RoundRobin(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: SecondOrderPredicate
  ) extends DistributionStrategy {
    override def distribute(matchGroup: MatchGrouping): Unit = {
      /* FIXME
      1. RR should recall previously distributed matchings for a proper distribution
      2. Engine outputs a GroupMatching for each ending event found.
         The problem is that newer ending events contain previous ending events
         i.e. most of the patterns are repeated! If we blindly apply Round Robin
         we are going to send
       */
      val nWorkers = workers.size
      val workersMap = workers.zipWithIndex.map(_.swap).toMap
      ctx.log.info(s"Distributing ${matchGroup.size()} matches")
      matchGroup.iterator().asScala.zipWithIndex.foreach {
        case (coreMatch, i) =>
          val dcerMatch = Match(coreMatch)
          workersMap(i % nWorkers) ! Worker
            .Process(dcerMatch, predicate, ctx.self)
      }
    }
  }

  def parse(
      str: String,
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: SecondOrderPredicate
  ): Option[DistributionStrategy] = {
    str.toLowerCase match {
      case "sequential" => Some(Sequential(ctx, workers, predicate))
      case "roundrobin" => Some(RoundRobin(ctx, workers, predicate))
      case _            => None
    }
  }
}
