package dcer.core.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import dcer.common.data.{ActorAddress, Timer}
import dcer.core.actors.Manager.MatchGroupingId
import dcer.core.data.Match.MaximalMatch
import dcer.core.data.Match
import dcer.core.distribution.{Blueprint, Predicate}
import dcer.core.logging.TimeFilter
import dcer.common.serialization.CborSerializable

import scala.collection.immutable.Queue
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Worker {

  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  final case class ProcessMatch(
      id: MatchGroupingId,
      m: Match,
      sop: Predicate,
      replyTo: ActorRef[Manager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case class ProcessMaximalMatch(
      id: MatchGroupingId,
      m: MaximalMatch,
      blueprint: Blueprint,
      sop: Predicate,
      replyTo: ActorRef[Manager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case class ProcessBlueprint(
      id: MatchGroupingId,
      blueprint: Blueprint,
      ms: List[MaximalMatch],
      sop: Predicate,
      replyTo: ActorRef[Manager.MatchValidated]
  ) extends Command
      with CborSerializable
  // NB: stop will stop the worker immediately i.e. it is responsible of the user to
  // stop the worker after all jobs have been finished (see EngineManager).
  final case object Stop extends Command with CborSerializable
  final case object MatchProcessingFinished
      extends Command
      with CborSerializable

  def apply(): Behavior[Command] = {
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(
        workerServiceKey,
        ctx.self
      )
      /*
      Is restarting for any exception a good idea? We could also use `watch`.
      With our current implementation, when a worker dies, the EngineManager is not aware.
       */
      Behaviors
        .supervise {
          running(ctx)
        }
        .onFailure[Exception](SupervisorStrategy.restart)
    }
  }

  private def running(
      ctx: ActorContext[Command]
  ): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case ProcessMatch(id, m, sop, replyTo) =>
        processMatch(ctx, id, m, sop, replyTo, Timer())

      case ProcessBlueprint(id, blueprint, maximalMatches, sop, replyTo) =>
        val (matches, repeated) = blueprint.enumerateDistinct(maximalMatches)
        ctx.log.info(
          s"${blueprint.pretty}: ${maximalMatches.length} maximal matches(${matches.length} sub matches)"
        )
        matches.foreach { m =>
          ctx.self ! ProcessMatch(id, m, sop, replyTo)
        }
        // Explained at EngineManager.scala
        (0 until repeated).foreach { _ =>
          replyTo ! Manager.MatchValidated(
            id,
            null,
            ActorAddress.parse(ctx.self.path.name).get,
            ctx.self,
            ignore = true
          )
        }
        Behaviors.same

      case ProcessMaximalMatch(id, maximalMatch, blueprint, sop, replyTo) =>
        val matches = blueprint.enumerate(maximalMatch)
        ctx.log.info(
          s"Maximal Match ${maximalMatch.events.toList
            .map(_.name)} and ${blueprint.pretty} contained ${matches.length} matches"
        )
        matches.foreach { m =>
          ctx.self ! ProcessMatch(id, m, sop, replyTo)
        }
        Behaviors.same

      case Stop =>
        ctx.log.info(s"Worker stopped")
        Behaviors.stopped

      case e =>
        ctx.log.error(s"Unexpected event: ${e.getClass.getName}")
        Behaviors.same
    }

  // For now, we simulate the execution-time cost of running the predicates.
  private def processMatch(
      ctx: ActorContext[Command],
      matchGroupingId: MatchGroupingId,
      m: Match,
      sop: Predicate,
      replyTo: ActorRef[Manager.MatchValidated],
      timer: Timer
  ): Behavior[Command] = {
    ctx.log.debug(
      s"Processing match (#events=${m.events.length}, complexity=$sop):"
    )

    // This amount of time is kinda arbitrary.
    // At some point, this would be replaced by a real evaluation of SOL predicates.
    val eventProcessingDuration = 10.millis

    // Why such a complex logic when we could Thread.sleep(n) ?
    // Thread.sleep may be problematic in the concurrency model of Akka.
    // If the scheduler is not clever enough, it may queue more than one actor per thread and
    // if the thread is blocked, all actors will be blocked although the rest could be
    // doing actual work.
    def waitMatchProcessing(
        queue: Queue[Command]
    ): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case MatchProcessingFinished =>
          ctx.log.info(
            TimeFilter.marker,
            s"Match (#events=${m.events.length}, complexity=$sop) processed in ${timer.elapsedTime().toMillis} milliseconds"
          )
          replyTo ! Manager.MatchValidated(
            matchGroupingId,
            m,
            ActorAddress.parse(ctx.self.path.name).get,
            ctx.self,
            ignore = false
          )
          queue.foreach { msg => ctx.self ! msg }
          running(ctx)

        // We accumulate (in order) the rest of messages for later
        case e: Command =>
          waitMatchProcessing(queue.enqueue(e))
      }
    }

    // Mock computational time depending on the complexity
    val n =
      sop match {
        case Predicate.Linear() =>
          m.events.length.toDouble
        case Predicate.Quadratic() =>
          scala.math.pow(m.events.length.toDouble, 2)
        case Predicate.Cubic() =>
          scala.math.pow(m.events.length.toDouble, 3)
      }

    val duration: FiniteDuration = eventProcessingDuration * n.toLong
    ctx.scheduleOnce(duration, ctx.self, MatchProcessingFinished)
    waitMatchProcessing(Queue.empty)
  }
}
