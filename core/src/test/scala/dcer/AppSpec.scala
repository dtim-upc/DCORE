package dcer

import dcer.actors.EngineManager.MatchGroupingId
import dcer.data.{
  Callback,
  DoubleValue,
  Engine,
  Event,
  Match,
  Port,
  QueryPath,
  StringValue,
  Worker
}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should._

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}

class AppSpec extends AsyncFlatSpec with Matchers with Expected {

  behavior of "App"

  it should "find all matches" in {
    implicit val executionContext: ExecutionContext =
      ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

    val result: AtomicReference[MyMap] =
      new AtomicReference[MyMap](Map.empty)

    val p = Promise[MyMap]()

    def waitOrThrow(timeout: Duration = 10.seconds): Future[_] = {

      val waitTask = Future {
        blocking {
          while (!isReady(result.get())) {
            Thread.sleep(1000)
          }
          p.success(result.get())
        }
      }

      val _ = Future {
        blocking {
          Thread.sleep(timeout.toMillis)
          if (!p.isCompleted) { p.failure(new RuntimeException("Timeout!")) }
        }
      }

      waitTask
    }

    def callback(id: MatchGroupingId, m: Match): Unit = {
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

    waitOrThrow()

    val query = QueryPath("./core/src/test/resources/query_1").get
    StartUp.startup(
      Engine,
      Port.SeedPort,
      Some(query),
      Some(Callback(callback))
    )
    StartUp.startup(Worker, Port.RandomPort, None, None)
    StartUp.startup(Worker, Port.RandomPort, None, None)
    StartUp.startup(Worker, Port.RandomPort, None, None)

    p.future.map { result =>
      expectedResult.foreach { case (id, expectedMatches) =>
        // TODO Create a new class MatchComp that overrides the equals with only some fields comparisons
        // then, use the list matches from Scalatest to compare the matches
        val matches = result(id)
        expectedMatches
      }
    }
  }
}

sealed trait Expected {
  type MyMap = Map[MatchGroupingId, List[Match]]

  def eventT(temp: Double): Event =
    Event(
      name = "T",
      streamName = "S",
      timestamp = -1L,
      index = -1L,
      attributes = Map(
        "temp" -> DoubleValue(temp),
        "city" -> StringValue("barcelona")
      )
    )

  def eventH(hum: Double): Event =
    Event(
      name = "T",
      streamName = "S",
      timestamp = -1L,
      index = -1L,
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

  def isReady(result: MyMap): Boolean = {
    if (expectedResult.size != result.size) {
      return false
    }

    result.foreach { case (k, matches) =>
      val expectedMatches = expectedResult(k)
      if (matches.size != expectedMatches.size) {
        return false
      }
    }

    true
  }
}
