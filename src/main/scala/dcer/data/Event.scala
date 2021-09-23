package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.runtime.events.{Event => JEvent}

/*
JEvent is more complicated since it stores:
- fields: Array[AnyRef]
- fieldDescriptions: Map[String, ValueType]

and has methods to retrieves those fields (fields can be of any type).

The main problem is that serialization seems to not work on Object
(see https://doc.akka.io/docs/akka/current/serialization-jackson.html#security).

For now, we are going to only store the fields that we are using i.e. the ones used for .toString()
and left the most complicated part for when it is needed
 */
case class Event(
    timestamp: Long,
    index: Long,
    name: String,
    streamName: String
) extends CborSerializable {
  // In the java version, this also prints the fields of the event
  override def toString: String = {
    s"Event($streamName, $name, idx=$index, ts=$timestamp)"
  }
}

object Event {
  def apply(javaEvent: JEvent): Event = {
    Event(
      timestamp = javaEvent.getTimestamp,
      index = javaEvent.getIndex,
      name = javaEvent.getName,
      streamName = javaEvent.getStreamName
    )
  }
}
