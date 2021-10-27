package dcer.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import better.files._
import dcer.actors.EngineManager.MatchGroupingFound
import dcer.data.{Configuration, DistributionStrategy, QueryPath}
import edu.puc.core.engine.BaseEngine
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.runtime.events.Event
import edu.puc.core.util.StringUtils

import java.io.BufferedReader
import java.util.regex.Pattern
import scala.util.Try

object Engine {

  sealed trait Command
  final case object Start extends Command
  final case class NextEvent(event: Option[Event]) extends Command

  def apply(
      queryPath: QueryPath,
      engineManager: ActorRef[EngineManager.Event]
  ): Behavior[Engine.Command] = {
    Behaviors.setup { ctx =>
      val configParser = Configuration(ctx)
      val baseEngine = buildEngine(configParser, queryPath) match {
        case Left(err)     => throw err
        case Right(engine) => engine
      }

      running(ctx, engineManager, baseEngine, groupingCount = 0L)
    }
  }

  private def running(
      ctx: ActorContext[Engine.Command],
      engineManager: ActorRef[EngineManager.Event],
      baseEngine: BaseEngine,
      groupingCount: Long
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
            val newGroupingCount =
              result match {
                case None => groupingCount
                case Some(matchGrouping) =>
                  ctx.log.info(
                    s"MatchGrouping (size = ${matchGrouping.size()}) found"
                  )
                  engineManager ! MatchGroupingFound(
                    groupingCount,
                    matchGrouping
                  )
                  groupingCount + 1
              }
            ctx.self ! NextEvent(Option(baseEngine.nextEvent()))
            running(ctx, engineManager, baseEngine, newGroupingCount)
          case None =>
            ctx.log.info("No more events on the source stream")
            ctx.log.info("Engine stopped")
            BaseEngine.clear() // Remove static variables
            engineManager ! EngineManager.EngineStopped
            Behaviors.stopped
        }
    }
  }

  private def buildEngine(
      configParser: Configuration.Parser,
      queryPath: QueryPath
  ): Either[Throwable, BaseEngine] =
    for {
      queryFile <- getQueryFile(
        configParser,
        File(queryPath.value + "/query_test.data")
      )
      streamFile <- Try(
        StringUtils.getReader(queryPath.value + "/stream_test.data")
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

  /*
   Some strats require the query to be changed.
   For example, MaximalMatchesEnumeration expects the query to return maximal matches.
   */
  def getQueryFile(
      configParser: Configuration.Parser,
      queryDataFile: File
  ): Either[Throwable, BufferedReader] = {
    val Same = Right(queryDataFile.newBufferedReader)

    for {
      strategy <- Try(
        configParser.getValueOrThrow(Configuration.DistributionStrategyKey)(
          DistributionStrategy.parse
        )
      ).toEither
      queryBuffer <- strategy match {
        case DistributionStrategy.Sequential =>
          Same
        case DistributionStrategy.RoundRobin =>
          Same
        case DistributionStrategy.RoundRobinWeighted =>
          Same
        case DistributionStrategy.PowerOfTwoChoices =>
          Same
        case DistributionStrategy.MaximalMatchesEnumeration =>
          newMaximalMatchQueryFile(queryDataFile)
      }
    } yield queryBuffer
  }

  /*
   This method replaces the "query_test.data" with one where the file
   storing the query has been replaced by a temporal file that contains
   the same query but replacing "SELECT *" by "SELECT MAX *".

   This method fails if:
   - The file does not follow the "query_test.data" format.
   - The query is not in FILE format.
   */
  def newMaximalMatchQueryFile(
      original: File
  ): Either[Throwable, BufferedReader] =
    original.lineIterator.toList match {
      case first :: second :: Nil =>
        val method = second.split(":")(0)
        val queryPath = second.split(":")(1)
        val query = File(queryPath).contentAsString
        if (query.contains("SELECT *")) {
          val newQuery =
            Pattern.quote("SELECT *").r.replaceFirstIn(query, "SELECT MAX *")
          val tmpQueryFile = File
            .newTemporaryFile()
            .deleteOnExit()
            .appendText(newQuery)
          Right(
            File
              .newTemporaryFile()
              .deleteOnExit()
              .appendLines(first, s"$method:$tmpQueryFile")
              .newBufferedReader
          )
        } else {
          Left(new RuntimeException("Query does not contain \"SELECT *\""))
        }

      case _ =>
        Left(
          new RuntimeException(
            s"${original.name} does not follow the query description file format:\n\n${original.contentAsString}"
          )
        )
    }

}
