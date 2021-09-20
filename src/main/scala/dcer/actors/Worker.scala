package dcer.actors

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.Match

import scala.concurrent.duration.DurationInt

object Worker {

  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  final case class Process(
      m: Match,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case object Stop extends Command with CborSerializable

  def apply(
      sop: SecondOrderPredicate
  ): Behavior[Worker.Command] = {
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(
        workerServiceKey,
        ctx.self
      )

      /*
      Is restarting for any exception a good idea? We could also use `watch`.
      With our current implementation, when a worker dies, the EngineManager is not aware.
       */
      Behaviors
        .supervise {
          running(ctx, sop)
        }
        .onFailure[Exception](SupervisorStrategy.restart)
    }
  }

  private def running(
      ctx: ActorContext[Worker.Command],
      sop: SecondOrderPredicate
  ): Behavior[Worker.Command] =
    Behaviors.receiveMessage[Command] {
      case Process(m, replyTo) =>
        ctx.log.info(s"Match received. Started processing...")
        processMatch(m, sop, replyTo)
        Behaviors.same

      case Stop =>
        ctx.log.info(s"Stopping...")
        Behaviors.stopped
    }

  // ***** Move out *******

  sealed trait SecondOrderPredicate
  object SecondOrderPredicate {
    case object Linear extends SecondOrderPredicate
    case object Quadratic extends SecondOrderPredicate
    case object Cubic extends SecondOrderPredicate
  }

  private def processMatch(
      m: Match,
      sop: SecondOrderPredicate,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ): Unit = {
    val eventProcessingDuration = 5.millis
    sop match {
      case SecondOrderPredicate.Linear =>
        m.forEach { _ =>
          Thread.sleep(eventProcessingDuration.toMillis)
        }
        replyTo ! EngineManager.MatchValidated(m)
      case SecondOrderPredicate.Quadratic =>
        m.forEach { _ =>
          m.forEach { _ =>
            Thread.sleep(eventProcessingDuration.toMillis)
          }
        }
        replyTo ! EngineManager.MatchValidated(m)
      case SecondOrderPredicate.Cubic =>
        m.forEach { _ =>
          m.forEach { _ =>
            m.forEach { _ =>
              Thread.sleep(eventProcessingDuration.toMillis)
            }
          }
        }
        replyTo ! EngineManager.MatchValidated(m)
    }
  }

  // ***********************
}
