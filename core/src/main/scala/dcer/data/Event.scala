package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.parser.plan.values.ValueType
import edu.puc.core.runtime.events.{Event => CoreEvent}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.Try

case class Event(
    name: String,
    streamName: String,
    timestamp: Long = -1,
    index: Long = -1,
    attributes: Map[String, Value] = Map.empty
) extends CborSerializable {
  override def toString: String = {
    s"$name($index)"
  }

  def pretty(): String = {
    val builder = StringBuilder.newBuilder
    attributes.foreach { case (name, value) =>
      builder ++= s", $name=$value"
    }
    s"Event($streamName, $name, idx=$index, ts=$timestamp ${builder.result()})"
  }

  def getValue(attrName: String): Option[Value] =
    this.attributes.get(attrName)

  /* Example:
   *
   *  val x: Option[Double] =
   *    get("temp") {
   *      case DoubleValue(d) => d
   *      case IntValue(i) => i.toDouble
   *      case StringValue(s) => s.toDouble
   *    }
   */
  def get[T](attrName: String)(f: PartialFunction[Value, T]): Option[T] =
    getValue(attrName).flatMap(f.lift)
}

object Event {
  def apply(coreEvent: CoreEvent): Event = {
    Event(
      name = coreEvent.getName,
      streamName = coreEvent.getStreamName,
      timestamp = coreEvent.getTimestamp,
      index = coreEvent.getIndex,
      attributes = getAttributes(coreEvent)
    )
  }

  private def getAttributes(coreEvent: CoreEvent): Map[String, Value] = {
    def unsafeCast[T](v: AnyRef)(implicit tag: ClassTag[T]): T = {
      tag.runtimeClass.cast(v).asInstanceOf[T]
    }

    coreEvent.getFieldDescriptions.asScala.foldLeft(Map.empty[String, Value]) {
      case (acc, tuple) =>
        val attrName = tuple.getKey
        val description = tuple.getValue
        val obj: java.lang.Object = coreEvent.getValue(attrName)
        val value: Value =
          description match {
            case ValueType.NUMERIC =>
              DoubleValue(unsafeCast[java.lang.Double](obj))
            case ValueType.INTEGER =>
              // FIXME
              // A value tagged as ValueType.INTEGER is parsed as a java.lang.Double
              // Example: Event A(id int)
              Try(IntValue(unsafeCast[java.lang.Integer](obj)))
                .getOrElse(DoubleValue(unsafeCast[java.lang.Double](obj)))
            case ValueType.LONG =>
              LongValue(unsafeCast[java.lang.Long](obj))
            case ValueType.DOUBLE =>
              DoubleValue(unsafeCast[java.lang.Double](obj))
            case ValueType.STRING =>
              StringValue(unsafeCast[java.lang.String](obj))
            case ValueType.BOOLEAN =>
              BoolValue(unsafeCast[java.lang.Boolean](obj))
          }
        acc + (attrName -> value)
    }
  }
}
