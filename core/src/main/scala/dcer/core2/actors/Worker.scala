package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.common.data.{ActorAddress, QueryPath}
import dcer.core2.actors.Manager.WorkerFinished

object Worker {
  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  case class Start(process: Int, processes: Int, replyTo: Manager.Ref)
      extends Command
  case class EngineFinished() extends Command

  type Ref = ActorRef[Worker.Command]

  def apply(queryPath: QueryPath): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(
        workerServiceKey,
        ctx.self
      )

      val engineRef =
        ctx.spawn(Engine(queryPath, ctx.self), "Engine")

      Behaviors
        .supervise {
          waitingStart(ctx, engineRef)
        }
        .onFailure[Exception](SupervisorStrategy.stop)
    }
  }

  private def waitingStart(
      ctx: ActorContext[Command],
      engine: Engine.Ref
  ): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case Worker.Start(process, processes, replyTo) =>
        engine ! Engine.Start(process, processes)
        waitingEngine(ctx, replyTo)

      case c: Command =>
        ctx.log.error(
          s"Received an unexpected command ${c.getClass.getName}."
        )
        Behaviors.stopped
    }

  private def waitingEngine(
      ctx: ActorContext[Command],
      replyTo: Manager.Ref
  ): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case EngineFinished() =>
        replyTo ! WorkerFinished(
          ctx.self,
          ActorAddress.parse(ctx.self.path.name).get
        )
        Behaviors.stopped

      case c: Command =>
        ctx.log.error(
          s"Received an unexpected command ${c.getClass.getName}."
        )
        Behaviors.stopped
    }
  }
}
