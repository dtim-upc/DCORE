package dcer.data

import dcer.Implicits
import org.scalatest.funspec.AnyFunSpec
import edu.puc.core.runtime.events.{Event => CoreEvent}

class EventSpec extends AnyFunSpec {
  describe("Event") {
    val (_, _) = Implicits.getEngine()

    describe("getValue") {
      it(
        "should return the given value if the attribute exists and the type corresponds"
      ) {
        val temp: Double = 0.2
        val coreEvent = new CoreEvent("S", "T", temp.asInstanceOf[AnyRef])
        val event = Event(coreEvent)
        event.getValue[Double]("temp") match {
          case Left(throwable) => fail(throwable.getMessage)
          case Right(result)   => assert(result === temp)
        }
      }
      it("should fail if the attribute does not exist") {
        val temp: Double = 0.2
        val coreEvent = new CoreEvent("S", "T", temp.asInstanceOf[AnyRef])
        val event = Event(coreEvent)
        val left = event.getValue[Double]("oops").left.get
        assert(left.getMessage === "Attribute oops does not exist")
      }
      it("should fail if the attribute is not of the given type") {
        val temp: Double = 0.2
        val coreEvent = new CoreEvent("S", "T", temp.asInstanceOf[AnyRef])
        val event = Event(coreEvent)
        val left = event.getValue[String]("temp").left.get
        assert(
          left.getMessage === "Cannot cast java.lang.Double to java.lang.String"
        )
      }
    }
  }
}
