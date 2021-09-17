package dcer.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object EngineManager {
  sealed trait Event

  // Having different "states" would be grate to avoid having a default case in the matches
  // but it is difficult to implement since Behavior is contravariant.
  // sealed trait RunningEvent extends Event
  // sealed trait WarmUpEvent extends Event

  private final case class WorkersUpdated(
      updatedWorkers: Set[ActorRef[Worker.Command]]
  ) extends Event
  final case object WarmUpDone extends Event
  final case class MatchGroupFound(matchGroup: MatchGrouping) extends Event
  final case object Stop extends Event

  def apply(
      queryPath: String,
      warmUpTime: FiniteDuration = 5.seconds
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(WarmUpDone, warmUpTime)

        val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
          case Worker.workerServiceKey.Listing(updatedWorkers) =>
            WorkersUpdated(updatedWorkers)
        }
        ctx.system.receptionist ! Receptionist.Subscribe(
          Worker.workerServiceKey,
          subscriptionAdapter
        )

        warming(ctx, queryPath, Set.empty)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, queryPath, newWorkers)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        startEngine(queryPath, workers).narrow

      case e: Event =>
        ctx.log.error(s"Received a ${e.toString} at warming.")
        Behaviors.stopped
    }

  private def startEngine(
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
      engine ! Engine.Start
      running(ctx, workers)
    }

  private def running(
      ctx: ActorContext[Event],
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        // For now, just ignore changes in the topology since the management could be complicated.
        ctx.log.warn(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        running(ctx, workers)

      case Stop =>
        ctx.log.info("Stopping EngineManager...")
        workers.foreach { worker =>
          worker ! Worker.Stop
        }
        Behaviors.stopped

      case MatchGroupFound(_) =>
        ctx.log.error("MatchGroup not implemented")
        Behaviors.stopped

      case e: Event =>
        ctx.log.error(s"Received a ${e.toString} at warming.")
        Behaviors.stopped
    }
  }
}
