package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.common.data.{ActorAddress, Predicate, QueryPath}
import dcer.common.serialization.CborSerializable
import dcer.core2.actors.Manager.WorkerFinished

object Worker {
  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command

  case class Start(
      process: Int,
      processes: Int,
      replyTo: Manager.Ref,
      predicate: Predicate
  ) extends Command
      with CborSerializable

  case class EngineFinished() extends Command

  // Stop the worker if it is not going to be used.
  case class Stop(replyTo: Manager.Ref) extends Command with CborSerializable

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
      case Worker.Start(process, processes, replyTo, predicate) =>
        ctx.log.info(
          s"Start(process=$process, processes=$processes) received at worker ${ctx.self.path}"
        )
        engine ! Engine.Start(process, processes, predicate)
        waitingEngine(ctx, replyTo)

      case Worker.Stop(replyTo) =>
        ctx.log.info(s"Stopping worker ${ctx.self.path} by stop message.")
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
        ctx.log.info(
          s"Stopping worker ${ctx.self.path}"
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
