package dcer.core2.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Worker {
  sealed trait Command
  def apply(): Behavior[Command] = {
    Behaviors.setup { _ =>
      Behaviors.receiveMessage { _ =>
        Behaviors.same
      }
    }
  }
}
