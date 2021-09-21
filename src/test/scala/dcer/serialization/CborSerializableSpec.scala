package dcer.serialization

import akka.actor._
import akka.serialization._
import dcer.data.Event
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.engine.{BaseEngine, Engine}
import edu.puc.core.runtime.events.{Event => JEvent}
import edu.puc.core.util.StringUtils
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec

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

  describe("Serialization") {
    // CORE depends on many static classes and variables.
    // For example, without an engine, Event does not work.
    val _ = getEngine()
    it("should round-trip serialize an Event") {
      /*
      DECLARE EVENT T(temp double)
      DECLARE EVENT H(hum double)
      DECLARE STREAM S(T, H)
       */
      val temp: Double = 23.0
      val jEvent = new JEvent("S", "T", temp.asInstanceOf[AnyRef])
      val event = Event(jEvent)
      roundTrip(event)
    }
  }
}
