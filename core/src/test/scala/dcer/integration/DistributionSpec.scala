package dcer.integration

import dcer.actors.EngineManager.MatchGroupingId
import dcer.data._
import dcer.{MatchTest, StartUp}
import org.scalatest.Assertion
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should._

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}

/** This spec validates that the same query returns the same result no matter
  * what strategy is used. This guarantees that '''all distribution strategies
  * are well implemented'''.
  *
  * FIXME Logging output is silenced by the appender and it should be outputted
  * on an error but this doesn't happen.
  */
class DistributionSpec extends AsyncFunSpec {
  describe("Distribution") {
    List(Test1).foreach { test =>
      describe(test.getClass.getName) {
        DistributionStrategy.all.foreach { strategy =>
          describe(strategy.toString) {
            it("should return the expected output") {
              test.runTest(strategy, timeout = 20.seconds)
            }
          }
        }
      }
    }
  }
}

object Test1 extends Test with Query1

trait Test extends CallbackProvider with Matchers {
  this: Query =>
  def runTest(
      strategy: DistributionStrategy,
      timeout: Duration
  ): Future[Assertion] = {
    implicit val executionContext: ExecutionContext =
      ExecutionContext.fromExecutorService(
        Executors.newFixedThreadPool(8)
      )

    val (callback, promise) =
      getPromiseAndCallback(timeout)(executionContext)

    StartUp.startup(
      Engine,
      Port.SeedPort,
      Some(query),
      Some(callback),
      Some(strategy)
    )
    StartUp.startup(Worker, Port.RandomPort)
    StartUp.startup(Worker, Port.RandomPort)
    StartUp.startup(Worker, Port.RandomPort)

    promise.future.map { result =>
      expectedResult.foreach { case (id, expectedMatches) =>
        val expectedMatchesTest = expectedMatches.map(MatchTest(_))
        val matchesTest = result(id).map(MatchTest(_))
        matchesTest should contain theSameElementsAs expectedMatchesTest
      }
      succeed
    }
  }
}

sealed trait Types {
  type MyMap = Map[MatchGroupingId, List[Match]]
}

sealed trait CallbackProvider extends Types {
  def getPromiseAndCallback(
      timeout: Duration = 30.seconds
  )(implicit executionContext: ExecutionContext): (Callback, Promise[MyMap]) = {
    val result: AtomicReference[MyMap] =
      new AtomicReference[MyMap](Map.empty)

    val p = Promise[MyMap]()

    val callback = {
      def matchFound(id: MatchGroupingId, m: Match): Unit = {
        result.updateAndGet { (map: MyMap) =>
          map.get(id) match {
            case Some(matches) =>
              val newMatches = matches ++ List(m)
              map + (id -> newMatches)
            case None =>
              map + (id -> List(m))
          }
        }
        ()
      }

      def exit(): Unit = {
        p.success(result.get())
      }

      Callback(matchFound, exit)
    }

    Future {
      blocking {
        Thread.sleep(timeout.toMillis)
        if (!p.isCompleted) { p.failure(new RuntimeException("Timeout!")) }
      }
    }

    (callback, p)
  }
}

//////////////////////////////////////
/////////// Queries //////////////////
//////////////////////////////////////

sealed trait Query extends Types {
  def query: QueryPath
  def expectedResult: MyMap
}

sealed trait Query1 extends Query {

  def eventT(temp: Double): Event =
    Event(
      name = "T",
      streamName = "S",
      attributes = Map(
        "temp" -> DoubleValue(temp),
        "city" -> StringValue("barcelona")
      )
    )

  def eventH(hum: Double): Event =
    Event(
      name = "H",
      streamName = "S",
      attributes = Map(
        "hum" -> DoubleValue(hum),
        "city" -> StringValue("barcelona")
      )
    )

  override val query: QueryPath =
    QueryPath("./core/src/test/resources/query_1").get

  override val expectedResult: MyMap = Map(
    0L -> List(
      Match(
        events = List(
          eventT(-2),
          eventH(30),
          eventH(65)
        ),
        nodeList = List(
        )
      ),
      Match(
        events = List(
          eventT(-2),
          eventH(20),
          eventH(65)
        ),
        nodeList = List(
        )
      ),
      Match(
        events = List(
          eventT(-2),
          eventH(30),
          eventH(20),
          eventH(65)
        ),
        nodeList = List(
        )
      )
    )
  )
}
