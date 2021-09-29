package dcer.actors

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import dcer.data
import dcer.data.{Configuration, Match}
import dcer.distribution.{DistributionStrategy, SecondOrderPredicate}
import dcer.logging.MatchFilter
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.MatchGrouping

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object EngineManager {

  // Having different "states" would be grate to avoid having a default case in the matches
  // but it is difficult to implement since Behavior is contravariant.
  // sealed trait RunningEvent extends Event
  // sealed trait WarmUpEvent extends Event

  sealed trait Event
  private final case class WorkersUpdated(
      updatedWorkers: Set[ActorRef[Worker.Command]]
  ) extends Event
  final case object WarmUpDone extends Event
  final case class MatchGroupFound(matchGroup: MatchGrouping) extends Event
  final case class MatchValidated(m: Match) extends Event with CborSerializable
  final case object Stop extends Event

  def apply(
      queryPath: String,
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

        warming(ctx, queryPath, Set.empty)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, queryPath, newWorkers)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        startEngine(queryPath, workers).narrow

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at warming state"
        )
        Behaviors.stopped
    }

  private def startEngine(
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]]
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
      engine ! Engine.Start

      val config = Configuration(ctx)

      val predicate: SecondOrderPredicate =
        config.getValueOrThrow(Configuration.SecondOrderPredicateKey)(
          SecondOrderPredicate.parse
        )

      val ds: DistributionStrategy =
        config.getValueOrThrow(Configuration.DistributionStrategyKey)(
          DistributionStrategy.parse(_, ctx, workers, predicate)
        )

      running(ctx, workers, ds)
    }

  private def running(
      ctx: ActorContext[Event],
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy,
      isStopping: Boolean = false
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        if (isStopping) {
          // FIXME
          // If a new worker joins during the shutdown process, the EngineManager won't be able to stop.
          if (newWorkers.isEmpty) {
            ctx.log.info("EngineManager stopped")
            Behaviors.stopped
          } else {
            running(ctx, newWorkers, ds, isStopping)
          }
        } else {
          // For now, just ignore changes in the topology since the management could be complicated.
          ctx.log.warn(
            "List of services registered with the receptionist changed: {}",
            newWorkers
          )
          running(ctx, workers, ds)
        }

      case Stop =>
        // Workers won't stop until they have finished processing all their work.
        ctx.log.info("Stopping EngineManager...")
        workers.foreach { worker =>
          worker ! Worker.Stop
        }
        running(ctx, workers, ds, isStopping = true)

      case MatchGroupFound(matchGrouping) =>
        ds.distribute(matchGrouping)
        Behaviors.same

      case MatchValidated(m) =>
        // This is logged into:
        // * stdout
        // * target/matches-XXXXX.log
        ctx.log.info(
          MatchFilter.marker,
          s"${data.Match.pretty(m)}"
        )
        Behaviors.same

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at running state"
        )
        Behaviors.stopped
    }
  }
}
