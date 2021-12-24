package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import dcer.common.CSV
import dcer.common.data.{Predicate, QueryPath}
import dcer.common.logging.{MatchFilter, StatsFilter}
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
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Engine {

  sealed trait Command
  final case class Start(process: Int, processes: Int, predicate: Predicate)
      extends Command
  final case class NextEvent(event: Option[Event]) extends Command
  final case object PredicateFinished extends Command

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
              case Left(err) =>
                ctx.log.error("Error on buildEngine", err)
                throw err
              case Right(engine) => engine
            }
          }
          ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
          running(ctx, replyTo, baseEngine, predicate, process)
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
      predicate: Predicate,
      process: Int
  ): Behavior[Engine.Command] = {
    Behaviors.receiveMessage {
      case NextEvent(event) =>
        event match {
          case Some(event) =>
            ctx.log.info("Event received: " + event.toString)
            processNewEvent(baseEngine, event)
            ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
            running(ctx, replyTo, baseEngine, predicate, process)

          case None =>
            ctx.log.info("No more events.\nStopping the engine.")
            logStats(ctx.log, process)
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
      // The order is important: matchCallback then start.
      engine.setMatchCallback(getPredicateCallback(ctx, predicate))
      engine.start(true)
      engine
    }

  private def logStats(logger: Logger, process: Int): Unit = {
    val toMs: Long => Double = x => x.toDouble / 1.0e6d
    val header = List(
      "process",
      "update_time_ms",
      "enumeration_time_ms",
      "complex_events",
      "garbage_collections"
    )
    val updateTime = toMs(Profiler.getExecutionTime)
    val enumerationTime = toMs(Profiler.getEnumerationTime)
    val complexEvents = Profiler.getNumberOfMatches
    val garbageCollections = Profiler.getCleanUps
    val csv = CSV.toCSV(
      header = Some(header),
      values = List(
        List[Any](
          process,
          updateTime,
          enumerationTime,
          complexEvents,
          garbageCollections
        )
      )
    )
    logger.info(StatsFilter.marker, csv)
  }

  private def processNewEvent(engine: BaseEngine, event: Event): Unit = {
    // Send will trigger the update and enumeration phase.
    engine.sendEvent(event)
  }

  // This should be eventually implemented as REAL second-order predicates.
  // Be careful not blocking the actor and skipping a heartbeat.
  private def getPredicateCallback(
      ctx: ActorContext[Engine.Command],
      predicate: Predicate
  ): Consumer[CDSComplexEventGrouping[_]] = {
    val (applyPredicate, predicateSimulationPerEvent)
        : (Boolean, FiniteDuration) =
      predicate match {
        case Predicate.None() =>
          (false, null)
        case Predicate.Linear() =>
          (true, Predicate.EventProcessingDuration)
        case Predicate.Quadratic() =>
          (true, Predicate.EventProcessingDuration * 2)
        case Predicate.Cubic() =>
          (true, Predicate.EventProcessingDuration * 3)
      }
    // Same as MatchCallback.printCallback but with predicates on ComplexEvents.
    def callback(complexEvents: CDSComplexEventGrouping[_]): Unit = {
      ctx.log.info(
        "Event " + complexEvents.getLastEvent + " triggered matches:"
      )
      complexEvents.forEach { complexEvent =>
        if (complexEvent ne null) {
          Profiler.incrementMatches()
          val builder = new StringBuilder("")
          builder ++= "Interval [" + complexEvent.getStart + ", " + complexEvent.getEnd + "]: "
          // Be careful, complex event is mutated after each iteration of the enumeration.
          // If you want to store the ComplexEvent you need to clone it.
          complexEvent.forEach { event =>
            // Thread.sleep(0) is way slower than a conditional instruction.
            if (applyPredicate) {
              Thread.sleep(predicateSimulationPerEvent.toMillis)
            }
            builder ++= event.toString
            builder ++= " "
          }
          ctx.log.info(
            MatchFilter.marker,
            builder.result()
          )
        }
      }
      ctx.log.info("")
    }

    callback
  }
}
