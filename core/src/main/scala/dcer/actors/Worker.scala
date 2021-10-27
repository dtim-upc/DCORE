package dcer.actors

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import dcer.actors.EngineManager.MatchGroupingId
import dcer.data.Match.MaximalMatch
import dcer.data.{ActorAddress, Match, Timer}
import dcer.distribution.{Blueprint, Predicate}
import dcer.logging.TimeFilter
import dcer.serialization.CborSerializable

import scala.collection.immutable.Queue
import scala.concurrent.duration.DurationInt

object Worker {

  val workerServiceKey: ServiceKey[Command] = ServiceKey[Command]("Worker")

  sealed trait Command
  final case class ProcessMatch(
      id: MatchGroupingId,
      m: Match,
      sop: Predicate,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case class ProcessMaximalMatch(
      id: MatchGroupingId,
      m: MaximalMatch,
      blueprint: Blueprint,
      sop: Predicate,
      replyTo: ActorRef[EngineManager.MatchValidated]
  ) extends Command
      with CborSerializable
  final case object Stop extends Command with CborSerializable
  final case object Tick extends Command with CborSerializable

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

      // TODO
      // Doesn't work since Stop goes first on the queue
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
      replyTo: ActorRef[EngineManager.MatchValidated],
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
    def go(
        n: Int,
        queue: Queue[Command]
    ): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Tick =>
          val rem = n - 1
          if (rem <= 0) {
            ctx.log.info(
              TimeFilter.marker,
              s"Match (#events=${m.events.length}, complexity=$sop) processed in ${timer.elapsedTime().toMillis} milliseconds"
            )
            replyTo ! EngineManager.MatchValidated(
              matchGroupingId,
              m,
              ActorAddress.parse(ctx.self.path.name).get,
              ctx.self
            )
            queue.foreach { msg => ctx.self ! msg }
            running(ctx)
          } else {
            val _ = ctx.scheduleOnce(eventProcessingDuration, ctx.self, Tick)
            go(rem, queue)
          }

        case e: Command =>
          go(n, queue.enqueue(e))
      }
    }
    val n =
      sop match {
        case Predicate.Linear() =>
          m.events.length.toDouble
        case Predicate.Quadratic() =>
          scala.math.pow(m.events.length.toDouble, 2)
        case Predicate.Cubic() =>
          scala.math.pow(m.events.length.toDouble, 3)
      }

    ctx.self ! Tick
    go(n.toInt, Queue.empty)
  }
}
