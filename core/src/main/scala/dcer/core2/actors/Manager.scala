package dcer.core2.actors

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import dcer.common.data.{ActorAddress, Configuration}
import dcer.common.serialization.CborSerializable
import dcer.core2.distribution.Distributor

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Manager {

  sealed trait Event

  private final case class WorkersUpdated(
      updatedWorkers: Set[Worker.Ref]
  ) extends Event

  final case object WarmUpDone extends Event

  case class WorkerFinished(worker: Worker.Ref, address: ActorAddress)
      extends Event
      with CborSerializable

  type Ref = ActorRef[Event]

  def apply(
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

        warming(ctx, Set.empty)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      workers: Set[Worker.Ref]
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, newWorkers)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        ctx.log.info(s"Number of workers: ${workers.size}")

        if (workers.isEmpty) {
          ctx.log.error(s"No worker has connected to the cluster.")
          Behaviors.stopped
        } else {
          val config = Configuration(ctx)
          val distributor = Distributor.fromConfig(config)(ctx, workers.toArray)
          distributor.distributeWorkload()
          waitingWorkers(
            ctx,
            workers
          )
        }

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at warming state"
        )
        Behaviors.stopped
    }

  private def waitingWorkers(
      ctx: ActorContext[Event],
      workers: Set[Worker.Ref]
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      case WorkerFinished(worker, address) =>
        ctx.log.info(
          s"Worker ${address.actorName}(${address.id.get})[${address.address}] finished."
        )
        val newWorkers = workers - worker
        if (newWorkers.isEmpty) {
          ctx.log.info("All workers have finished. Stopping manager.")
          Behaviors.stopped
        } else {
          waitingWorkers(ctx, newWorkers)
        }

      case WorkersUpdated(_) =>
        // We are ignoring changing in the connected workers for now.
        // In the future, we could take into account changes in the network.
        waitingWorkers(ctx, workers)

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at waitingWorkers state"
        )
        Behaviors.stopped
    }
  }
}
