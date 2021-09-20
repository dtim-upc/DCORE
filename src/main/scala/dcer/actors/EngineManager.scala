package dcer.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match, MatchGrouping}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import dcer.data

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
      warmUpTime: FiniteDuration = 5.seconds,
      ds: DistributionStrategy
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

        warming(ctx, queryPath, Set.empty, ds)
      }
    }

  private def warming(
      ctx: ActorContext[Event],
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy
  ): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        warming(ctx, queryPath, newWorkers, ds)

      case WarmUpDone =>
        ctx.log.info("Warm up finished.")
        startEngine(queryPath, workers, ds).narrow

      case e: Event =>
        ctx.log.error(s"Received a ${e.toString} at warming.")
        Behaviors.stopped
    }

  private def startEngine(
      queryPath: String,
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy
  ): Behavior[Event] =
    Behaviors.setup { ctx =>
      val engine = ctx.spawn(Engine(queryPath, ctx.self), "Engine")
      engine ! Engine.Start
      running(ctx, workers, ds)
    }

  private def running(
      ctx: ActorContext[Event],
      workers: Set[ActorRef[Worker.Command]],
      ds: DistributionStrategy
  ): Behavior[Event] = {
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        // For now, just ignore changes in the topology since the management could be complicated.
        ctx.log.warn(
          "List of services registered with the receptionist changed: {}",
          newWorkers
        )
        running(ctx, workers, ds)

      case Stop =>
        ctx.log.info("Stopping EngineManager...")
        workers.foreach { worker =>
          worker ! Worker.Stop
        }
        Behaviors.stopped

      case MatchGroupFound(matchGroup) =>
        distributeMatchGroup(ctx, workers, matchGroup, ds)
        Behaviors.same

      // TODO
      // We need to implement different strategies such as store to file.
      case MatchValidated(m) =>
        val pretty = data.Match.pretty(m)
        ctx.log.info(s"**** Match found *****\n${pretty}")
        ctx.log.info(s"**********************")
        Behaviors.same

      case e: Event =>
        ctx.log.error(s"Received a ${e.toString} at warming.")
        Behaviors.stopped
    }
  }

  // ***** Move out *******

  sealed trait DistributionStrategy
  object DistributionStrategy {
    case object None extends DistributionStrategy
    case object RoundRobin extends DistributionStrategy
    case object DoubleHashing extends DistributionStrategy
    case object SubMatchesFromMaximalMatch extends DistributionStrategy
    case object SubMatchesBySizeFromMaximalMatch extends DistributionStrategy
    case object NoCollisionsEnumeration extends DistributionStrategy
  }

  import scala.collection.JavaConverters._

  private def distributeMatchGroup(
      ctx: ActorContext[EngineManager.Event],
      workers: Set[ActorRef[Worker.Command]],
      matchGroup: MatchGrouping,
      ds: DistributionStrategy
  ): Unit =
    ds match {
      case DistributionStrategy.None =>
        throw new RuntimeException("Not implemented")
      case DistributionStrategy.RoundRobin =>
        // FIXME
        // The implementation doesn't take into account previous MatchGroupings
        // We need some mechanism to store the state
        val nWorkers = workers.size
        val workersMap = workers.zipWithIndex.map(_.swap).toMap
        matchGroup.iterator().asScala.zipWithIndex.foreach { case (m, i) =>
          workersMap(i % nWorkers) ! Worker.Process(m, ctx.self)
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

  // *********************
}
