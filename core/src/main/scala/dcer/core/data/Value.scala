package dcer.core.data

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import dcer.common.serialization.CborSerializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[BoolValue], name = "BoolValue"),
    new JsonSubTypes.Type(value = classOf[IntValue], name = "IntValue"),
    new JsonSubTypes.Type(value = classOf[LongValue], name = "LongValue"),
    new JsonSubTypes.Type(value = classOf[DoubleValue], name = "DoubleValue"),
    new JsonSubTypes.Type(value = classOf[StringValue], name = "StringValue")
  )
)
sealed trait Value extends CborSerializable
case class BoolValue(b: Boolean) extends Value
case class IntValue(i: Int) extends Value
case class LongValue(l: Long) extends Value
case class DoubleValue(d: Double) extends Value
case class StringValue(s: String) extends Value
