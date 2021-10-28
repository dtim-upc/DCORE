package dcer.actors

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import dcer.data
import dcer.data._
import dcer.distribution.Distributor
import dcer.logging.{MatchFilter, TimeFilter}
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/* Graceful shutdown

The Manager needs to monitor the state (active/inactive/stopped) of each worker.
The state should include the load of each worker.
Workers should communicate with the Manager once their finished their work.
Manager should also listen when a worker is stopped.

When the Engine has stopped because there are no more events to process,
the Engine Manager should set to a final state.

At the final state, the Manager should send a Stop message as soon
as a worker reaches 0 load. It is guaranteed that a worker will finish its load
since no more matches are being generated at the final state and the work done
in a worker is total and finite.

Once a worker receives a Stop message, it should unconditionally shutdown.
Once all workers have stopped, the Manager should stop itself.
 */

object EngineManager {
  sealed trait Event
  private final case class WorkersUpdated(
      updatedWorkers: Set[ActorRef[Worker.Command]]
  ) extends Event
  final case object WarmUpDone extends Event
  type MatchGroupingId = Long
  final case class MatchGroupingFound(
      id: MatchGroupingId,
      matchGroup: MatchGrouping
  ) extends Event
  final case class MatchValidated(
      id: MatchGroupingId,
      m: Match,
      from: ActorAddress,
      fromRef: ActorRef[Worker.Command]
  ) extends Event
      with CborSerializable
  final case object EngineStopped extends Event

  type Workers = Map[ActorRef[Worker.Command], Long]

  def apply(
      queryPath: QueryPath,
      callback: Option[Callback],
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

        warming(ctx, queryPath, callback, Set.empty)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      queryPath: QueryPath,
      callback: Option[Callback],
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, queryPath, callback, newWorkers)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        ctx.log.info(s"Number of workers: ${workers.size}")
        startEngine(queryPath, callback, workers, Timer()).narrow

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at warming state"
        )
        Behaviors.stopped
    }

  private def startEngine(
      queryPath: QueryPath,
      callback: Option[Callback],
      workers: Set[ActorRef[Worker.Command]],
      timer: Timer
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
      engine ! Engine.Start

      val config = Configuration(ctx)

      val distributor: Distributor =
        Distributor.fromConfig(config)(ctx, workers.toArray)

      val workersAndLoad: Workers = workers.map((_, 0L)).toMap
      running(ctx, callback, workersAndLoad, distributor, timer)
    }

  private def running(
      ctx: ActorContext[Event],
      callback: Option[Callback],
      workers: Workers,
      distributor: Distributor,
      timer: Timer,
      isStopping: Boolean = false
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      // When a new worker connects or disconnects, a WorkersUpdated is send with the list
      // of all workers (the old ones and the new ones).
      case WorkersUpdated(newWorkers) =>
        if (isStopping) {
          // Are all workers stopped?
          if (newWorkers.isEmpty) { // Yes
            val timeElapsedSinceStart = timer.elapsedTime()
            ctx.log.info(
              TimeFilter.marker,
              s"All events processed in ${timeElapsedSinceStart.toMillis} milliseconds"
            )
            ctx.log.info("EngineManager stopped")
            callback.foreach { case Callback(_, exit) =>
              exit()
            }
            Behaviors.stopped
          } else { // No
            Behaviors.same
          }
        } else {
          // For now, just ignore changes in the topology since the management could be complicated.
          // If a worker is removed, we panic.
          // If a worker is added, we ignore it
          // In the future, we could handle these changes.
          ctx.log.warn(
            "List of services registered with the receptionist changed: {}",
            newWorkers
          )
          running(ctx, callback, workers, distributor, timer, isStopping)
        }

      case EngineStopped =>
        ctx.log.info("Stopping EngineManager...")
        // First we stop all workers that have no work assigned.
        workers.foreach {
          case (worker, load) if load == 0 =>
            worker ! Worker.Stop
          case _ => () // otherwise it throws
        }
        // Then, we set the state to stopping.
        // We need to wait for each worker to finish its share before stopping.
        running(ctx, callback, workers, distributor, timer, isStopping = true)

      case MatchGroupingFound(id, matchGrouping) =>
        import cats.implicits._
        val assignment = distributor.distribute(id, matchGrouping)
        val newWorkers = workers |+| assignment
        running(
          ctx,
          callback,
          newWorkers,
          distributor,
          timer,
          isStopping
        )

      case MatchValidated(id, m, from, fromRef) =>
        ctx.log.info(
          MatchFilter.marker,
          s"Match found at ${from.actorName}(${from.id.get})[${from.address}]:\n${data.Match.pretty(m)}"
        )
        callback match {
          case Some(Callback(matchFound, _)) => matchFound(id, m)
          case None                          => ()
        }
        val newLoad: Long = workers(fromRef) - 1
        if (isStopping && newLoad == 0) {
          // It is safe to stop the worker
          fromRef ! Worker.Stop
        }
        running(
          ctx,
          callback,
          workers.updated(fromRef, newLoad),
          distributor,
          timer,
          isStopping
        )

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at running state"
        )
        Behaviors.stopped
    }
  }
}
