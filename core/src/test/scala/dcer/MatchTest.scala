package dcer

import dcer.core.data.{Event, Match, Value}

/** Match class is hard to compare since you have to get the events' timestamp
  * correct which is utterly unnecessary for testing equality.
  *
  * This class is a newtype over Match to redefine equality.
  *
  * TODO nodeList is not taken into consideration
  */
case class MatchTest(events: List[EventTest]) extends AnyVal

object MatchTest {
  def apply(m: Match): MatchTest = {
    MatchTest(m.events.toList.map(EventTest(_)))
  }
}

case class EventTest(
    name: String,
    streamName: String,
    attributes: Map[String, Value]
)

object EventTest {
  def apply(e: Event): EventTest = {
    EventTest(e.name, e.streamName, e.attributes)
  }
}
