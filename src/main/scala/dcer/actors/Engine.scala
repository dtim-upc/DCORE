package dcer.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.actors.EngineManager.MatchGroupFound
import edu.puc.core.engine.BaseEngine
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.runtime.events.Event
import edu.puc.core.util.StringUtils

import scala.util.Try

/* Notes
Files with logic from DistributedCER:
- MatchesOperator.java
- SubmatchEnumerator.java
 */
object Engine {

  sealed trait Command
  final case object Start extends Command
  final case class NextEvent(event: Option[Event]) extends Command

  def apply(
      queryPath: String,
      engineManager: ActorRef[EngineManager.Event]
  ): Behavior[Engine.Command] = {
    Behaviors.setup { ctx =>
      val baseEngine = buildEngine(queryPath) match {
        case Left(err)     => throw err
        case Right(engine) => engine
      }

      running(ctx, engineManager, baseEngine)
    }
  }

  private def running(
      ctx: ActorContext[Engine.Command],
      engineManager: ActorRef[EngineManager.Event],
      baseEngine: BaseEngine
  ): Behavior[Engine.Command] = {
    Behaviors.receiveMessage {
      case Start =>
        baseEngine.start()
        val event = Option(baseEngine.nextEvent())
        ctx.self ! NextEvent(event)
        Behaviors.same
      case NextEvent(event) =>
        event match {
          case Some(event) =>
            val result = Option(baseEngine.new_sendEvent(event))
            ctx.log.info("Event send: " + event.toString)
            result match {
              case None => ()
              case Some(matchGroup) =>
                ctx.log.info(
                  s"MatchGrouping (size = ${matchGroup.size()}) found"
                )
                engineManager ! MatchGroupFound(matchGroup)
            }
            ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
            Behaviors.same
          case None =>
            ctx.log.info("No more events on the source stream")
            ctx.log.info("Engine stopped")
            engineManager ! EngineManager.Stop
            Behaviors.stopped
        }
    }
  }

  private def buildEngine(queryPath: String): Either[Throwable, BaseEngine] =
    for {
      queryFile <- Try(
        StringUtils.getReader(queryPath + "/query_test.data")
      ).toEither
      streamFile <- Try(
        StringUtils.getReader(queryPath + "/stream_test.data")
      ).toEither
      executorManager <- Try(ExecutorManager.fromCOREFile(queryFile)).toEither
      streamManager <- Try(StreamManager.fromCOREFile(streamFile)).toEither
      engine <- Try(
        BaseEngine.newEngine(
          executorManager,
          streamManager,
          false, // logMetrics
          true, // fastRun: do not wait between events using the timestamps
          true // offline: do not create the RMI server
        )
      ).toEither
    } yield engine
}
