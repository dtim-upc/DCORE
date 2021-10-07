package dcer.unit

import dcer.Common
import dcer.data.{DoubleValue, Event, StringValue}
import edu.puc.core.runtime.events.{Event => CoreEvent}
import org.scalatest.funspec.AnyFunSpec

class EventSpec extends AnyFunSpec {
  describe("Event") {
    val (_, _) = Common.getGlobalEngine()

    describe("getValue") {
      it(
        "should return the given value if the attribute exists"
      ) {
        val temp: java.lang.Double = 0.2
        val city: java.lang.String = "Osaka"
        val coreEvent = new CoreEvent(
          "S",
          "T",
          temp,
          city
        )
        val event = Event(coreEvent)
        event.getValue("temp") match {
          case Some(DoubleValue(d)) => assert(d === temp)
          case Some(_)              => fail("temp is not of the expected type")
          case None                 => fail("temp not found")
        }
        event.getValue("city") match {
          case Some(StringValue(s)) => assert(s === city)
          case Some(_)              => fail("city is not of the expected type")
          case None                 => fail("city not found")
        }
      }

      it("should return None if the attribute does not exist") {
        val temp: java.lang.Double = 0.2
        val city: java.lang.String = "Osaka"
        val coreEvent = new CoreEvent(
          "S",
          "T",
          temp,
          city
        )
        val event = Event(coreEvent)
        assert(event.getValue("oops").isEmpty)
      }
    }
  }
}
