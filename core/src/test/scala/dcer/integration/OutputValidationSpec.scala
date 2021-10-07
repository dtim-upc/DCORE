package dcer.integration

import dcer.actors.EngineManager.MatchGroupingId
import dcer.data._
import dcer.{MatchTest, StartUp}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should._

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}

class OutputValidationSpec
    extends AsyncFunSpec
    with Matchers
    with CallbackProvider
    with Expectations {

  describe("Output Validation") {
    describe("Round Robin") {
      describe("Query 1") {
        it("should find all matches") {
          implicit val executionContext: ExecutionContext =
            ExecutionContext.fromExecutorService(
              Executors.newFixedThreadPool(8)
            )

          val (callback, promise) =
            getPromiseAndCallback(timeout = 10.seconds)(executionContext)

          StartUp.startup(
            Engine,
            Port.SeedPort,
            Some(QueryPath("./core/src/test/resources/query_1").get),
            Some(callback)
          )
          StartUp.startup(Worker, Port.RandomPort, None, None)
          StartUp.startup(Worker, Port.RandomPort, None, None)
          StartUp.startup(Worker, Port.RandomPort, None, None)

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

sealed trait Expectations extends Types {

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

  val expectedResult: MyMap = Map(
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
