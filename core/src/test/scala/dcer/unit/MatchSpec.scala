package dcer.unit

import dcer.data.{Event, Match}
import org.scalatest.funspec.AnyFunSpec

class MatchSpec extends AnyFunSpec {
  val streamName = "S"
  val eventA: Event = Event(name = "A", streamName)
  val eventB: Event = Event(name = "B", streamName)
  val eventC: Event = Event(name = "C", streamName)
  val eventD: Event = Event(name = "D", streamName)
  val eventE: Event = Event(name = "E", streamName)
  val allEvents = List(eventA, eventB, eventC, eventD, eventE)

  def getMatch(events: List[Event]): Match = {
    val nodeList = events.map(allEvents.indexOf(_))
    Match(events, nodeList)
  }

  describe("Match") {
    describe("weight") {
      it("should return the weight of a match (1)") {
        val m = getMatch(events = List(eventA, eventB, eventC))
        assert(Match.weight(m) == 3L)
      }
      it("should return the weight of a match (2)") {
        val m = getMatch(events = List(eventA, eventB, eventB, eventB, eventC))
        assert(Match.weight(m) == 9L)
      }
      it("should return the weight of a match (3)") {
        val m = getMatch(events =
          List(eventA, eventB, eventB, eventC, eventD, eventD, eventE)
        )
        assert(Match.weight(m) == 9L)
      }
      it("should return the weight of a match (4)") {
        val m = getMatch(events = List(eventA, eventB, eventB, eventB, eventB))
        assert(Match.weight(m) == 16L)
      }
    }
  }
}
