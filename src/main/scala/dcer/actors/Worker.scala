package dcer.actors

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.data.Match
import dcer.distribution.SecondOrderPredicate
import dcer.serialization.CborSerializable

import scala.concurrent.duration.DurationInt

object Worker {

  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  final case class Process(
      m: Match,
      sop: SecondOrderPredicate,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case object Stop extends Command with CborSerializable

  def apply(): Behavior[Worker.Command] = {
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
          running(ctx)
        }
        .onFailure[Exception](SupervisorStrategy.restart)
    }
  }

  private def running(
      ctx: ActorContext[Worker.Command]
  ): Behavior[Worker.Command] =
    Behaviors.receiveMessage[Command] {
      case Process(m, sop, replyTo) =>
        ctx.log.info(s"Match received. Started processing...")
        processMatch(m, sop, replyTo)
        Behaviors.same

      case Stop =>
        ctx.log.info(s"Stopping...")
        Behaviors.stopped
    }

  /** This method is a mock which will be eventually replaced by real second-order predicates. */
  private def processMatch(
      m: Match,
      sop: SecondOrderPredicate,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ): Unit = {
    val eventProcessingDuration = 5.millis
    sop match {
      case SecondOrderPredicate.Linear() =>
        m.events.foreach { _ =>
          Thread.sleep(eventProcessingDuration.toMillis)
        }
        replyTo ! EngineManager.MatchValidated(m)
      case SecondOrderPredicate.Quadratic() =>
        m.events.foreach { _ =>
          m.events.foreach { _ =>
            Thread.sleep(eventProcessingDuration.toMillis)
          }
        }
        replyTo ! EngineManager.MatchValidated(m)
      case SecondOrderPredicate.Cubic() =>
        m.events.foreach { _ =>
          m.events.foreach { _ =>
            m.events.foreach { _ =>
              Thread.sleep(eventProcessingDuration.toMillis)
            }
          }
        }
        replyTo ! EngineManager.MatchValidated(m)
    }
  }
}
