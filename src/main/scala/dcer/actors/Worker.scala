package dcer.actors

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.serialization.CborSerializable

object Worker {

  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  final case class Say(text: String) extends Command with CborSerializable
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
      case Say(text) =>
        ctx.log.info(s"Text $text received!")
        Behaviors.same
      case Stop =>
        ctx.log.info(s"Stopping...")
        Behaviors.stopped
    }
}
