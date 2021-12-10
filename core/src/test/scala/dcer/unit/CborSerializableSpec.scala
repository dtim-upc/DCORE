package dcer.unit

import akka.actor._
import akka.serialization._
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import dcer.Common
import dcer.data.{Event, Match}
import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match => JMatch}
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

sealed trait IDoNotSerialize
case object IJustDoNotSerialize extends IDoNotSerialize
case class Message(x: IDoNotSerialize) extends CborSerializable

/*
This test verifies that the messages can be passed from one JVM to another JVM using JacksonCborSerializer.
The test fails if the message cannot be serialized and deserialized.
 */
class CborSerializableSpec extends AnyFunSpec with Matchers {
  private def roundTrip[T <: CborSerializable](
      original: T
  )(implicit clazz: Class[_ <: T]): Assertion = {
    val system = ActorSystem("example")
    val serialization = SerializationExtension(system)

    val serializer = serialization.findSerializerFor(original)
    assert(
      serializer.getClass.getName === "akka.serialization.jackson.JacksonCborSerializer"
    )

    val bytes: Array[Byte] = serializer.toBinary(original)
    val back: AnyRef = serializer.fromBinary(bytes, clazz)

    assert(back.asInstanceOf[T] == original)
  }

  private def roundTrip2(original: AnyRef): Assertion = {
    val system = ActorSystem("example")
    val serialization = SerializationExtension(system)

    val serializer = serialization.findSerializerFor(original)
    assert(
      serializer.getClass.getName === "akka.serialization.jackson.JacksonCborSerializer"
    )
    val id = serializer.identifier
    val manifest = Serializers.manifestFor(
      serialization.findSerializerFor(original),
      original
    )

    val bytes = serialization.serialize(original).get
    val back = serialization.deserialize(bytes, id, manifest).get

    assert(back === original)
  }

  describe("Serialization") {
    // CORE depends on many static classes and variables.
    // For example, without an engine, Event does not work.
    val (_, producer) = Common.getNewEngine()

    it("should round-trip serialize an Event") {
      val event = Event(producer.getEventAtRandom())
      roundTrip(event)(event.getClass)
      roundTrip2(event)
    }

    it("should round-trip serialize a Match") {
      val jMatch = new JMatch()
      (1 to 10).map(i => (producer.getEventAtRandom(), i)).foreach {
        case (event, _) =>
          jMatch.push(event)
      }
      val m = Match(jMatch)
      roundTrip(m)(m.getClass)
      roundTrip2(m)
    }

    it(
      "should fail the round-trip serialization if the message cannot be serialized"
    ) {
      val message = Message(IJustDoNotSerialize)
      assertThrows[InvalidDefinitionException] {
        roundTrip(message)(message.getClass)
      }
      assertThrows[InvalidDefinitionException] {
        roundTrip2(message)
      }
    }
  }
}
