package dcer.integration

import dcer.common.data
import dcer.common.data.{Callback, Master, Port, QueryPath, Slave}
import dcer.core.actors.Manager.MatchGroupingId
import dcer.core.data.DistributionStrategy.MaximalMatchesEnumeration
import dcer.core.data._
import dcer.{MatchTest, Init}
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

  // This tests may fail with "Socket 25251 being used"
  // Just re-run the tests and it will eventually succeed
  describe("Distribution") {
    List(Test1, Test2).foreach { test =>
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
object Test2 extends Test with Query2

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

    Init.startCore(
      Master,
      Port.SeedPort,
      Some(query),
      Some(callback),
      Some(strategy)
    )
    Init.startCore(Slave, Port.RandomPort)
    Init.startCore(Slave, Port.RandomPort)
    Init.startCore(Slave, Port.RandomPort)

    promise.future.map { result =>
      expectedResult.foreach { case (id, expectedMatches) =>
        val expectedMatchesTest = expectedMatches.map(MatchTest(_))
        var matchesTest = result(id).map(MatchTest(_))
        if (strategy == MaximalMatchesEnumeration) {
          // Recall MaximalMatchesEnumeration generates duplicates.
          matchesTest = matchesTest.distinct
        }
        matchesTest should have length (expectedMatchesTest.length.toLong)
        matchesTest should contain theSameElementsAs expectedMatchesTest
      }
      succeed
    }
  }
}

sealed trait Types {
  type MyMap = Map[MatchGroupingId, List[Match]]
  private val IgnoreNodeList: Array[Int] = Array.empty
  def getMatch(events: Array[Event]): Match =
    Match(
      events = events,
      nodeList = IgnoreNodeList
    )
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

      data.Callback(matchFound, exit)
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
      getMatch(
        Array(
          eventT(-2),
          eventH(30),
          eventH(65)
        )
      ),
      getMatch(
        Array(
          eventT(-2),
          eventH(20),
          eventH(65)
        )
      ),
      getMatch(
        Array(
          eventT(-2),
          eventH(30),
          eventH(20),
          eventH(65)
        )
      )
    )
  )
}

sealed trait Query2 extends Query {
  def event(eventName: String)(id: Double): Event =
    Event(
      name = eventName,
      streamName = "S",
      attributes = Map(
        "id" -> DoubleValue(id)
      )
    )

  def eventA(id: Double): Event = event("A")(id)
  def eventB(id: Double): Event = event("B")(id)
  def eventC(id: Double): Event = event("C")(id)

  override val query: QueryPath =
    QueryPath("./core/src/test/resources/query_2").get

  /*
   Stream: A1 B1 A2 B2 A3 B3 C1
   MatchGrouping 1: A1 B1 A2 B2 A3 B3 C1

     - MM: A1A2A3B3C1 (7 matches)
      - M: A1B3C1
      - M: A2B3C1
      - M: A3B3C1
      - M: A1A2B3C1
      - M: A1A3B3C1
      - M: A2A3B3C1
      - M: A1A2A3B3C1

     - MM: A1A2B2B3C1 (9 matches)
      - M: A1B2C1
      - M: A1B3C1 (repeated)
      - M: A1B2B3C1
      - M: A2B2C1
      - M: A2B3C1 (repeated)
      - M: A2B2B3C1
      - M: A1A2B2C1
      - M: A1A2B3C1 (repeated)
      - M: A1A2B2B3C1

     - MM: A1B1B2B3C1 (7 matches)
       - M: A1B1C1
       - M: A1B2C1 (repeated)
       - M: A1B3C1 (repeated)
       - M: A1B1B2C1
       - M: A1B1B3C1
       - M: A1B2B3C1 (repeated)
       - M: A1B1B2B3C1
   */
  override val expectedResult: MyMap = Map(
    0L -> List(
      // Maximal Match := A1A2A3B3C1
      getMatch(
        Array(
          eventA(1),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(2),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(3),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(1),
          eventA(2),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(1),
          eventA(3),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(2),
          eventA(3),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(1),
          eventA(2),
          eventA(3),
          eventB(3),
          eventC(1)
        )
      ),

      // Maximal Match := A1A2B2B3C1
      getMatch(
        Array(
          eventA(1),
          eventB(2),
          eventC(1)
        )
      ),
//      getMatch(
//        Array(
//          eventA(1),
//          eventB(3),
//          eventC(1)
//        )
//      ),
      getMatch(
        Array(
          eventA(1),
          eventB(2),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(2),
          eventB(2),
          eventC(1)
        )
      ),
//      getMatch(
//        Array(
//          eventA(2),
//          eventB(3),
//          eventC(1)
//        )
//      ),
      getMatch(
        Array(
          eventA(2),
          eventB(2),
          eventB(3),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(1),
          eventA(2),
          eventB(2),
          eventC(1)
        )
      ),
//      getMatch(
//        Array(
//          eventA(1),
//          eventA(2),
//          eventB(3),
//          eventC(1)
//        )
//      ),
      getMatch(
        Array(
          eventA(1),
          eventA(2),
          eventB(2),
          eventB(3),
          eventC(1)
        )
      ),

      // Maximal Match := A1B1B2B3C1
      getMatch(
        Array(
          eventA(1),
          eventB(1),
          eventC(1)
        )
      ),
//      getMatch(
//        Array(
//          eventA(1),
//          eventB(2),
//          eventC(1)
//        )
//      ),
//      getMatch(
//        Array(
//          eventA(1),
//          eventB(3),
//          eventC(1)
//        )
//      ),
      getMatch(
        Array(
          eventA(1),
          eventB(1),
          eventB(2),
          eventC(1)
        )
      ),
      getMatch(
        Array(
          eventA(1),
          eventB(1),
          eventB(3),
          eventC(1)
        )
      ),
//      getMatch(
//        Array(
//          eventA(1),
//          eventB(2),
//          eventB(3),
//          eventC(1)
//        )
//      ),
      getMatch(
        Array(
          eventA(1),
          eventB(1),
          eventB(2),
          eventB(3),
          eventC(1)
        )
      )
    )
  )
}
