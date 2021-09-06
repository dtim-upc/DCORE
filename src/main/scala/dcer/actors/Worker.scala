package dcer.actors

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import dcer.serialization.CborSerializable

object Worker {

  val workerServiceKey: ServiceKey[Say] = ServiceKey[Worker.Say]("Worker")

  sealed trait Command
  final case class Say(text: String) extends Command with CborSerializable

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(
        workerServiceKey,
        ctx.self
      )

      Behaviors.receiveMessage { case Say(text) =>
        ctx.log.info(s"Text $text received!")
        Behaviors.same
      }
    }
}
