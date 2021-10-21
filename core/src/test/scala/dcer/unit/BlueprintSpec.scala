package dcer.unit

import dcer.data.Match.MaximalMatch
import dcer.data.{Event, Match}
import dcer.distribution.Blueprint
import dcer.distribution.Blueprint.EventTypeSeqSize
import org.scalatest.funspec.AnyFunSpec

class BlueprintSpec extends AnyFunSpec {

  import dcer.unit.BlueprintSpec._

  // TODO
  // - [ ] Test fails
  // - [ ] Add fromMaximalMatch tests
  // - [ ] Add enumerate test

  describe("Blueprint") {
    describe("fromMaximalMatch") {
      allFromMaximalMatchTests.foreach {
        case (stream, maximalMatch, expectedResult) =>
          it(s"should generate all blueprints from $stream") {
            val result = Blueprint.fromMaximalMatch(maximalMatch)
            assert(result == expectedResult)
          }
      }
    }

    describe("enumerate") {
      it(s"TODO") {
        // TODO
      }
    }
  }
}

object BlueprintSpec {
  val streamName = "S"
  val A: Event = Event(name = "A", streamName)
  val B: Event = Event(name = "B", streamName)
  val C: Event = Event(name = "C", streamName)
  val D: Event = Event(name = "D", streamName)
  val E: Event = Event(name = "E", streamName)
  val allEvents: Array[Event] = Array(A, B, C, D, E)

  def getMatch(events: Array[Event]): Match = {
    val nodeList = events.map(allEvents.indexOf(_))
    Match(events, nodeList)
  }

  val test1 = {
    val stream = "ABBCCCD"
    val maximalMatch = getMatch(Array(A, B, B, C, C, C, D))
    val expectedResult = (
      List(
        Blueprint(Array(1, 1, 1, 1)),
        Blueprint(Array(1, 1, 2, 1)),
        Blueprint(Array(1, 1, 3, 1)),
        Blueprint(Array(1, 2, 1, 1)),
        Blueprint(Array(1, 2, 2, 1)),
        Blueprint(Array(1, 2, 3, 1))
      ),
      Array(1, 2, 3, 1)
    )
    (stream, maximalMatch, expectedResult)
  }

  val allFromMaximalMatchTests
      : List[(String, MaximalMatch, (List[Blueprint], EventTypeSeqSize))] =
    List(test1)
}
