package dcer.core2.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
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
import scala.collection.immutable.Queue
import scala.util.Try

/*
  Akka cluster expects the nodes to output a heartbeat every n seconds (couldn't find the parameter).
If you block the actor with a long sequential task, the node will not be able to send the heartbeat
and it will be considered down and the manager will remove it. This is not so problematic
in a real architecture since the node will get up eventually and reconnect but for our project it is.

  In the past, we did the enumeration and the application of the second-order predicates at the same place
but this blocked the actor for too long so we had to split it complicating the logic of this actor.
 */
object Engine {

  sealed trait Command
  final case class Start(process: Int, processes: Int, predicate: Predicate)
      extends Command
  final case class NextEvent(event: Option[Event]) extends Command
  final case class NewComplexEvent(sizeComplexEvent: Long) extends Command
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
              case Left(err)     => throw err
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
            printProfiler(ctx.log, process)
            replyTo ! EngineFinished()
            Behaviors.stopped
        }

      // processNewEvent will trigger a NewComplexEvent per match.
      // Then it will enqueue NextEvent. The NewComplexEvents will be processed first.
      // And then the NextEvent. This guarantees that no NewComplexEvent is
      // left to process when NextEvent is None and we exit.
      case NewComplexEvent(sizeComplexEvent) =>
        def waiting(
            queue: Queue[Engine.Command]
        ): Behaviors.Receive[Command] = {
          Behaviors.receiveMessage {
            case PredicateFinished =>
              queue.foreach { msg => ctx.self ! msg }
              running(ctx, replyTo, baseEngine, predicate, process)
            case e: Command =>
              waiting(queue.enqueue(e))
          }
        }
        Predicate.predicateSimulationDuration(
          predicate,
          sizeComplexEvent
        ) match {
          case Some(duration) =>
            ctx.scheduleOnce(
              duration,
              ctx.self,
              PredicateFinished
            )
          case None =>
            ctx.self ! PredicateFinished
        }
        waiting(Queue.empty)

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

  private def printProfiler(logger: Logger, process: Int): Unit = {
    val toSeconds: Long => Double = x => x.toDouble / 1.0e9d
    val pretty =
      s"""Process $process:
         |- Execution time: ${toSeconds(Profiler.getExecutionTime)} seconds.
         |- Enumeration time: ${toSeconds(Profiler.getEnumerationTime)} seconds.
         |- Complex events: ${Profiler.getNumberOfMatches}.
         |- CleanUps: ${Profiler.getCleanUps}.
         |""".stripMargin
    logger.info(StatsFilter.marker, pretty)
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

    val applyPredicate: Boolean = predicate != Predicate.None()

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
          var n: Long = 0
          complexEvent.forEach { event =>
            builder ++= event.toString
            builder ++= " "
            n += 1
          }
          ctx.log.info(
            MatchFilter.marker,
            builder.result()
          )
          // Why not sending the whole ComplexEvent ?
          // The problem is that the complex event is mutated after each iteration of the enumeration.
          // It is totally fine to apply the predicate after each iteration since it will not change until the next iteration.
          // But if you want to store it in memory, you need to clone it (which is not implemented yet).
          if (applyPredicate) {
            ctx.self ! NewComplexEvent(sizeComplexEvent = n)
          }
        }
      }
      ctx.log.info("")
    }

    callback
  }
}
