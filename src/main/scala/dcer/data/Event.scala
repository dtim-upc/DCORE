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
    attributes: Map[String, Any] = Map.empty
) extends CborSerializable {
  override def toString: String = {
    val builder = StringBuilder.newBuilder
    attributes.foreach { case (name, value) =>
      builder ++= s", $name=$value"
    }
    s"Event($streamName, $name, idx=$index, ts=$timestamp ${builder.result()})"
  }

  def getValue[T <: Any](
      attrName: String
  )(implicit tag: ClassTag[T]): Either[Throwable, T] = {
    this.attributes.get(attrName) match {
      case None =>
        Left(new RuntimeException(s"Attribute $attrName does not exist"))
      case Some(v) =>
        Try(tag.runtimeClass.cast(v).asInstanceOf[T]).toEither
    }
  }
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

  private def getAttributes(coreEvent: CoreEvent): Map[String, Any] = {
    def unsafeCast[T](v: AnyRef)(implicit tag: ClassTag[T]): T = {
      tag.runtimeClass.cast(v).asInstanceOf[T]
    }

    coreEvent.getFieldDescriptions.asScala.foldLeft(Map.empty[String, Any]) {
      case (acc, tuple) =>
        val attrName = tuple.getKey
        val description = tuple.getValue
        val obj: java.lang.Object = coreEvent.getValue(attrName)
        val value: Any =
          description match {
            case ValueType.NUMERIC =>
              throw new RuntimeException("")
//              obj.asInstanceOf[java.lang.Double].doubleValue()
            case ValueType.INTEGER =>
              throw new RuntimeException("")
//              obj.asInstanceOf[java.lang.Integer].intValue()
            case ValueType.LONG =>
              throw new RuntimeException("")
//              obj.asInstanceOf[java.lang.Long].longValue()
            case ValueType.DOUBLE =>
              println("Double!!!")
              unsafeCast[java.lang.Double](obj).doubleValue()
            case ValueType.STRING =>
              throw new RuntimeException("")
//              obj.asInstanceOf[java.lang.String]
            case ValueType.BOOLEAN =>
              throw new RuntimeException("")
//              obj.asInstanceOf[java.lang.Boolean].booleanValue()
          }
        acc + (attrName -> value)
    }
  }
}
