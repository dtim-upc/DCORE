package dcer.core2.actors

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object Worker {
  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command

  def apply(): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(
        workerServiceKey,
        ctx.self
      )
      Behaviors
        .supervise {
          running(ctx)
        }
        .onFailure[Exception](SupervisorStrategy.restart)
    }
  }

  private def running(ctx: ActorContext[Command]): Behavior[Command] =
    Behaviors.receiveMessage[Command] { case e =>
      ctx.log.error(s"Unexpected event: ${e.getClass.getName}")
      Behaviors.same
    }
}
