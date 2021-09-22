package dcer.serialization

import akka.actor._
import akka.serialization._
import dcer.data.{Event, Match}
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.engine.{BaseEngine, Engine}
import edu.puc.core.runtime.events.{Event => JEvent}
import edu.puc.core.execution.structures.output.{Match => JMatch}
import edu.puc.core.util.StringUtils
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec

import scala.util.Random

class CborSerializableSpec extends AnyFunSpec {
  private def roundTrip(original: AnyRef): Assertion = {
    val system = ActorSystem("example")
    val serialization = SerializationExtension(system)

    val bytes = serialization.serialize(original).get
    val serializerId = serialization.findSerializerFor(original).identifier
    val manifest = Serializers.manifestFor(
      serialization.findSerializerFor(original),
      original
    )

    val back = serialization.deserialize(bytes, serializerId, manifest).get

    assert(back === original)
  }

  /*
  DECLARE EVENT T(temp double)
  DECLARE EVENT H(hum double)
  DECLARE STREAM S(T, H)
   */
  private def getEngine(): Engine = {
    val queryPath: String = "./src/main/resources/query_0"
    val queryFile = StringUtils.getReader(queryPath + "/query_test.data")
    val streamFile = StringUtils.getReader(queryPath + "/stream_test.data")
    val executorManager = ExecutorManager.fromCOREFile(queryFile)
    val streamManager = StreamManager.fromCOREFile(streamFile)
    val engine = BaseEngine.newEngine(executorManager, streamManager)
    engine.start()
    engine
  }

  // You need to call .getEngine() before getting an event.
  private def getEventAtRandom(): JEvent = {
    val rng = new Random()
    rng.nextBoolean() match {
      case false =>
        val temp: Double = rng.nextDouble()
        new JEvent("S", "T", temp.asInstanceOf[AnyRef])
      case true =>
        val hum: Double = rng.nextDouble()
        new JEvent("S", "H", hum.asInstanceOf[AnyRef])
    }
  }

  describe("Serialization") {
    // CORE depends on many static classes and variables.
    // For example, without an engine, Event does not work.
    val _ = getEngine()

    it("should round-trip serialize an Event") {
      val event = Event(getEventAtRandom())
      roundTrip(event)
    }

    it("should round-trip serialize a Match") {
      val jMatch = new JMatch()
      (1 to 10).map(i => (getEventAtRandom(), i)).foreach { case (event, _) =>
        jMatch.push(event)
      }
      val m = Match(jMatch)
      roundTrip(m)
    }
  }
}
