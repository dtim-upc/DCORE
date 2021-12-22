package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.common.data.{Predicate, QueryPath}
import dcer.common.logging.StatsFilter
import dcer.core2.actors.Worker.EngineFinished
import edu.puc.core2.engine.BaseEngine
import edu.puc.core2.engine.executors.ExecutorManager
import edu.puc.core2.engine.streams.StreamManager
import edu.puc.core2.execution.structures.output.CDSComplexEventGrouping
import edu.puc.core2.runtime.events.Event
import edu.puc.core2.runtime.profiling.Profiler
import edu.puc.core2.util.{DistributionConfiguration, StringUtils}
import org.slf4j.Logger

import java.util.Optional
import java.util.function.Consumer
import java.util.logging.Level
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

object Engine {

  sealed trait Command
  final case class Start(process: Int, processes: Int, predicate: Predicate)
      extends Command
  final case class NextEvent(event: Option[Event]) extends Command

  type Ref = ActorRef[Engine.Command]

  def apply(
      queryPath: QueryPath,
      replyTo: Worker.Ref
  ): Behavior[Engine.Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Start(process, processes, predicate) =>
          ctx.log.info(s"Start received at engine.")
          val distributionConfiguration =
            new DistributionConfiguration(process, processes)
          val baseEngine = {
            buildEngine(
              ctx,
              queryPath,
              distributionConfiguration,
              predicate
            ) match {
              case Left(err)     => throw err
              case Right(engine) => engine
            }
          }
          ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
          running(ctx, replyTo, baseEngine, predicate)
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
      baseEngine: BaseEngine,
      predicate: Predicate
  ): Behavior[Engine.Command] = {
    Behaviors.receiveMessage {
      case NextEvent(event) =>
        event match {
          case Some(event) =>
            ctx.log.info("Event received: " + event.toString)
            processNewEvent(baseEngine, event)
            ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
            running(ctx, replyTo, baseEngine, predicate)

          case None =>
            ctx.log.info("No more events.\nStopping the engine.")
            printProfiler(ctx.log)
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
      ctx: ActorContext[Engine.Command],
      queryPath: QueryPath,
      distributionConfiguration: DistributionConfiguration,
      predicate: Predicate
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
        BaseEngine.newEngine(
          executorManager,
          streamManager,
          false, // logMetrics
          true, // fastRun: do not wait between events using the timestamps
          true // offline: do not create the RMI server
        )
      }.toEither
    } yield {
      BaseEngine.LOGGER.setLevel(Level.OFF) // Disable logging in CORE
      engine.start(true)
      engine.setMatchCallback(getPredicateCallback(ctx, predicate))
      engine
    }

  private def printProfiler(logger: Logger): Unit = {
    val toSeconds: Long => Double = x => x.toDouble / 1.0e9d
    val pretty =
      s"""Execution time: ${toSeconds(Profiler.getExecutionTime)} seconds.
         |Enumeration time: ${toSeconds(Profiler.getEnumerationTime)} seconds.
         |Complex events: ${Profiler.getNumberOfMatches}.
         |CleanUps: ${Profiler.getCleanUps}.
         |""".stripMargin
    logger.info(StatsFilter.marker, pretty)
  }

  private def processNewEvent(engine: BaseEngine, event: Event): Unit = {
    // Send will trigger the update and enumeration phase.
    engine.sendEvent(event)
  }

  // This should be eventually implemented as REAL second-order predicates.
  private def getPredicateCallback(
      ctx: ActorContext[Engine.Command],
      predicate: Predicate
  ): Consumer[CDSComplexEventGrouping[_]] = {
    val waitTimeOnEvent: FiniteDuration =
      predicate match {
        case Predicate.None() =>
          0.millis
        case Predicate.Linear() =>
          Predicate.EventProcessingDuration
        case Predicate.Quadratic() =>
          Predicate.EventProcessingDuration * 2
        case Predicate.Cubic() =>
          Predicate.EventProcessingDuration * 3
      }
    val waitTimeMs: Long = waitTimeOnEvent.toMillis

    // Same as MatchCallback.printCallback
    // but with predicates on ComplexEvents.
    def callback(complexEvents: CDSComplexEventGrouping[_]): Unit = {
      ctx.log.info(
        "Event " + complexEvents.getLastEvent + " triggered matches:"
      )
      complexEvents.forEach { complexEvent =>
        if (complexEvent ne null) {
          Profiler.incrementMatches()
          val builder = new StringBuilder("\t")
          builder ++= "Interval [" + complexEvent.getStart + ", " + complexEvent.getEnd + "]: "
          complexEvent.forEach { event =>
            Thread.sleep(waitTimeMs)
            builder ++= event.toString
            builder ++= " "
          }
          ctx.log.info(builder.result())
        }
      }
      ctx.log.info("")
    }

    callback
  }
}
