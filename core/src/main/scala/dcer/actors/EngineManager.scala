package dcer.actors

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cats.implicits._
import dcer.data._
import dcer.distribution.Distributor
import dcer.logging.{MatchFilter, StatsFilter, TimeFilter}
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

/*
Why does MatchValidated.ignore exist?

The engine manager waits for the load of each worker to be zero.
This load is retrieved from Distributor.scala.
The problem is that in some strategies the load a priori e.g. MaximalMatchesDisjointEnumeration.
Then, the only solutions are:
1. Change the whole implementation of the shutdown process.
2. Do a workaround.
For now, we pick (2). We set an upper bound of the load of the worker in the Engine Manager
and once the real load is computed we send as many MatchValidated with ignore=true as
the difference between this upper bound and the real load.
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
      fromRef: ActorRef[Worker.Command],
      ignore: Boolean /* Explained on the top of the file.*/
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

      running(
        ctx,
        callback,
        workersAndLoad,
        Statistics(workersAndLoad),
        distributor,
        timer
      )
    }

  private def running(
      ctx: ActorContext[Event],
      callback: Option[Callback],
      workers: Workers,
      stats: Statistics[ActorRef[Worker.Command], Long],
      distributor: Distributor,
      timer: Timer,
      isStopping: Option[Int] = None /* Number of remaining workers */
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      // When a new worker connects or disconnects, a WorkersUpdated is send with the list
      // of all workers (the old ones and the new ones).
      case WorkersUpdated(newWorkers) =>
        if (isStopping.isDefined) {
          if (newWorkers.nonEmpty) {
            Behaviors.same
          } else {
            val timeElapsedSinceStart = timer.elapsedTime()
            ctx.log.info(
              TimeFilter.marker,
              s"All events processed in ${timeElapsedSinceStart.toMillis} milliseconds"
            )
            ctx.log.info(
              StatsFilter.marker,
              s"""Matches by worker: ${stats.value.values.toList}
                |Coefficient of variation (CV): ${stats
                .coefficientOfVariation()}
                |""".stripMargin
            )
            ctx.log.info("EngineManager stopped")
            callback match {
              case Some(Callback(_, exit)) => exit()
              case None                    => ()
            }
            Behaviors.stopped
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
          running(
            ctx,
            callback,
            workers,
            stats,
            distributor,
            timer,
            isStopping
          )
        }

      case EngineStopped =>
        ctx.log.info("Stopping EngineManager...")
        // We need to wait for each worker to finish its share before stopping.
        running(
          ctx,
          callback,
          workers,
          stats,
          distributor,
          timer,
          isStopping = Some(workers.count { case (_, load) => load > 0 })
        )

      case MatchGroupingFound(id, matchGrouping) =>
        val assignment = distributor.distribute(id, matchGrouping)
        val newWorkers = workers |+| assignment
        val newStats = stats + Statistics(assignment)
        running(
          ctx,
          callback,
          newWorkers,
          newStats,
          distributor,
          timer,
          isStopping
        )

      case MatchValidated(id, m, from, fromRef, ignore) =>
        if (!ignore) {
          ctx.log.info(
            MatchFilter.marker,
            s"""Match found at ${from.actorName}(${from.id.get})[${from.address}]:
               |$m
               |""".stripMargin
          )

          callback match {
            case Some(Callback(matchFound, _)) => matchFound(id, m)
            case None                          => ()
          }
        }

        val newLoad: Long = workers(fromRef) - 1

        // In the past, we deleted workers individually once the load reached 0,
        // but akka started to panic when JVMs disconnected from the network.
        //
        // Now, we wait for all workers to finish before stopping them, all at once.
        val newIsStopping: Option[Int] =
          isStopping map { rem =>
            val newRem =
              if (newLoad == 0) { rem - 1 }
              else { rem }

            if (newRem == 0) {
              ctx.log.info("Stopping all workers...")
              workers.foreach { case (ref, _) =>
                ref ! Worker.Stop
              }
            }

            newRem
          }

        running(
          ctx,
          callback,
          workers.updated(fromRef, newLoad),
          stats,
          distributor,
          timer,
          newIsStopping
        )

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at running state"
        )
        Behaviors.stopped
    }
  }
}
