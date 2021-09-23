package dcer.actors

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import dcer.data
import dcer.data.Match
import dcer.distribution.{DistributionStrategy, SecondOrderPredicate}
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{
  MatchGrouping => JMatchGrouping
}

import scala.collection.JavaConverters._
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
  final case class MatchGroupFound(matchGroup: JMatchGrouping) extends Event
  final case class MatchValidated(m: Match) extends Event with CborSerializable
  final case object Stop extends Event

  def apply(
      queryPath: String,
      warmUpTime: FiniteDuration = 5.seconds,
      ds: DistributionStrategy,
      sop: SecondOrderPredicate
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

        warming(ctx, queryPath, Set.empty, ds, sop)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy,
      sop: SecondOrderPredicate
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, queryPath, newWorkers, ds, sop)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        startEngine(queryPath, workers, ds, sop).narrow

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at warming state"
        )
        Behaviors.stopped
    }

  private def startEngine(
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy,
      sop: SecondOrderPredicate
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
      engine ! Engine.Start
      running(ctx, workers, ds, sop)
    }

  private def running(
      ctx: ActorContext[Event],
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy,
      sop: SecondOrderPredicate,
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
            running(ctx, newWorkers, ds, sop, isStopping)
          }
        } else {
          // For now, just ignore changes in the topology since the management could be complicated.
          ctx.log.warn(
            "List of services registered with the receptionist changed: {}",
            newWorkers
          )
          running(ctx, workers, ds, sop)
        }

      case Stop =>
        // Workers won't stop until they have finished processing all their work.
        ctx.log.info("Stopping EngineManager...")
        workers.foreach { worker =>
          worker ! Worker.Stop
        }
        running(ctx, workers, ds, sop, isStopping = true)

      case MatchGroupFound(matchGroup) =>
        distributeMatchGroup(ctx, workers, matchGroup, ds, sop)
        Behaviors.same

      // TODO
      // We need to implement different strategies such as store to file.
      case MatchValidated(m) =>
        ctx.log.info(s"*********** Match found ***********")
        ctx.log.info(data.Match.pretty(m))
        ctx.log.info(s"***********************************")
        Behaviors.same

      case e: Event =>
        ctx.log.error(
          s"Received an unexpected Event.${e.getClass.getName} at running state"
        )
        Behaviors.stopped
    }
  }

  private def distributeMatchGroup(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      matchGroup: JMatchGrouping,
      ds: DistributionStrategy,
      sop: SecondOrderPredicate
  ): Unit =
    // TODO
    // We could create an abstract class with one class for each strategy
    // Each class would store its own state.
    // But share the same interface.
    ds match {
      case DistributionStrategy.NoStrategy =>
        throw new RuntimeException("Not implemented")
      case DistributionStrategy.RoundRobin =>
        // FIXME
        // The implementation doesn't take into account previous MatchGroupings
        val nWorkers = workers.size
        val workersMap = workers.zipWithIndex.map(_.swap).toMap
        matchGroup.iterator().asScala.zipWithIndex.foreach {
          case (coreMatch, i) =>
            val dcerMatch = Match(coreMatch)
            workersMap(i % nWorkers) ! Worker.Process(dcerMatch, sop, ctx.self)
        }
      case DistributionStrategy.DoubleHashing =>
        throw new RuntimeException("Not implemented")
      case DistributionStrategy.SubMatchesFromMaximalMatch =>
        throw new RuntimeException("Not implemented")
      case DistributionStrategy.SubMatchesBySizeFromMaximalMatch =>
        throw new RuntimeException("Not implemented")
      case DistributionStrategy.NoCollisionsEnumeration =>
        throw new RuntimeException("Not implemented")
    }
}
