package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.common.data.QueryPath
import dcer.core2.actors.Worker.EngineFinished
import edu.puc.core2.engine.BaseEngine
import edu.puc.core2.engine.executors.ExecutorManager
import edu.puc.core2.engine.streams.StreamManager
import edu.puc.core2.runtime.events.Event
import edu.puc.core2.util.{DistributionConfiguration, StringUtils}

import java.util.Optional
import java.util.logging.Level
import scala.util.Try

object Engine {

  sealed trait Command
  final case class Start(process: Int, processes: Int) extends Command
  final case class NextEvent(event: Option[Event]) extends Command

  type Ref = ActorRef[Engine.Command]

  def apply(
      queryPath: QueryPath,
      replyTo: Worker.Ref
  ): Behavior[Engine.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Start(process, processes) =>
          val distributionConfiguration =
            new DistributionConfiguration(process, processes)
          val baseEngine =
            buildEngine(queryPath, distributionConfiguration) match {
              case Left(err)     => throw err
              case Right(engine) => engine
            }
          running(ctx, replyTo, baseEngine)
        case c: Command =>
          ctx.log.error(
            s"Received an unexpected command ${c.getClass.getName}."
          )
          Behaviors.stopped
      }
    }
  }

  private def running(
      ctx: ActorContext[Engine.Command],
      replyTo: Worker.Ref,
      baseEngine: BaseEngine
  ): Behavior[Engine.Command] = {
    Behaviors.receiveMessage {
      case NextEvent(event) =>
        event match {
          case Some(event) =>
            // Send will trigger the update and enumeration phase.
            ctx.log.info("Event send: " + event.toString)
            baseEngine.sendEvent(event)

            ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
            running(ctx, replyTo, baseEngine)

          case None =>
            ctx.log.info("No more events...Stopping the engine.")
            replyTo ! EngineFinished()
            Behaviors.stopped
        }
      case c: Command =>
        ctx.log.error(
          s"Received an unexpected command ${c.getClass.getName}."
        )
        Behaviors.stopped
    }
  }

  private def buildEngine(
      queryPath: QueryPath,
      distributionConfiguration: DistributionConfiguration
  ): Either[Throwable, BaseEngine] =
    for {
      queryFile <- Try(
        StringUtils.getReader(queryPath.value + "/query_test.data")
      ).toEither
      streamFile <- Try(
        StringUtils.getReader(queryPath.value + "/stream_test.data")
      ).toEither
      executorManager <- Try(
        ExecutorManager.fromCOREFile(
          queryFile,
          Optional.of(distributionConfiguration)
        )
      ).toEither
      streamManager <- Try(StreamManager.fromCOREFile(streamFile)).toEither
      engine <- Try {
        BaseEngine.LOGGER.setLevel(Level.OFF) // Disable logging in CORE
        val engine = BaseEngine.newEngine(
          executorManager,
          streamManager,
          false, // logMetrics
          true, // fastRun: do not wait between events using the timestamps
          true // offline: do not create the RMI server
        )
        // By default engine will print the complex event to the stdout.
        // Don't do something like this: engine.setMatchCallback(math => ())
        // This will avoid enumerating the tECS.
        engine
      }.toEither
    } yield engine
}
