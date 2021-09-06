package dcer.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import edu.puc.core.execution.structures.output.MatchGrouping

object EngineManager {
  sealed trait Event
  final case class MatchGroupFound(matchGroup: MatchGrouping) extends Event
  final case object Done extends Event
  private final case class WorkersUpdated(
      updatedWorkers: Set[ActorRef[Worker.Say]]
  ) extends Event

  def apply(queryPath: String): Behavior[Event] = Behaviors.setup { ctx =>
    val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
      case Worker.workerServiceKey.Listing(updatedWorkers) =>
        WorkersUpdated(updatedWorkers)
    }

    ctx.system.receptionist ! Receptionist.Subscribe(
      Worker.workerServiceKey,
      subscriptionAdapter
    )

    val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
    engine ! Engine.Start

    running(ctx, Set.empty)
  }

  private def running(
      ctx: ActorContext[Event],
      workers: Set[ActorRef[Worker.Say]]
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        running(ctx, newWorkers)
      case Done =>
        // TODO EngineManager should stop all running workers and then, stop itself.
        ctx.log.info("Stopping EngineManager")
        Behaviors.stopped

      case _ =>
        running(ctx, workers)
    }
  }
}
