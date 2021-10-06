package dcer.distribution

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import dcer.actors.EngineManager.MatchGroupingId
import dcer.actors.{EngineManager, Worker}
import dcer.data.Match
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.collection.JavaConverters._

// TODO (optionally)
// Add workers dynamically
// This would affect the distribution strategies e.g. rebalance of work.

sealed trait Strategy {
  val ctx: ActorContext[EngineManager.Event]
  val workers: Set[ActorRef[Worker.Command]]
  val predicate: Predicate
  def distribute(id: MatchGroupingId, matchGroup: MatchGrouping): Unit
}

object Strategy {

  case class Sequential(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Strategy {
    override def distribute(
        id: MatchGroupingId,
        matchGroup: MatchGrouping
    ): Unit = {
      throw new RuntimeException("Not implemented")
    }
  }

  case class RoundRobin(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: Predicate
  ) extends Strategy {
    var lastIndex: Int = 0

    override def distribute(
        id: MatchGroupingId,
        matchGroup: MatchGrouping
    ): Unit = {
      ctx.log.info(s"Distributing ${matchGroup.size()} matches")
      val nWorkers = workers.size
      val workersMap = workers.zipWithIndex.map(_.swap).toMap // from 0 to n-1
      matchGroup.iterator().asScala.foreach { coreMatch =>
        val worker = workersMap(lastIndex)
        ctx.log.debug(s"Sending match to worker: ${worker.path}")
        worker ! Worker.Process(id, Match(coreMatch), predicate, ctx.self)
        lastIndex = (lastIndex + 1) % nWorkers
      }
    }
  }

  def parse(
      str: String,
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      predicate: Predicate
  ): Option[Strategy] = {
    str.toLowerCase match {
      case "sequential" => Some(Sequential(ctx, workers, predicate))
      case "roundrobin" => Some(RoundRobin(ctx, workers, predicate))
      case _            => None
    }
  }
}
