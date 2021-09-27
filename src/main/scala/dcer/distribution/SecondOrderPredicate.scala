package dcer.distribution

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import dcer.serialization.CborSerializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(
      value = classOf[SecondOrderPredicate.Linear],
      name = "linear"
    ),
    new JsonSubTypes.Type(
      value = classOf[SecondOrderPredicate.Quadratic],
      name = "quadratic"
    ),
    new JsonSubTypes.Type(
      value = classOf[SecondOrderPredicate.Cubic],
      name = "cubic"
    )
  )
)
sealed trait SecondOrderPredicate extends CborSerializable

object SecondOrderPredicate {
  // These should be case objects but serialization doesn't work out
  // of the box for case objects (see https://doc.akka.io/docs/akka/current/serialization-jackson.html#adt-with-trait-and-case-object)
  //
  // This is why we decided to use case classes with no parameters.
  case class Linear() extends SecondOrderPredicate
  case class Quadratic() extends SecondOrderPredicate
  case class Cubic() extends SecondOrderPredicate

  // Don't forget to update this
  val all: List[SecondOrderPredicate] =
    List(
      Linear(),
      Quadratic(),
      Cubic()
    )

  def parse(str: String): Option[SecondOrderPredicate] = {
    // Here we use .startsWith because case classes .toString
    all.find(_.toString.toLowerCase.startsWith(str.toLowerCase))
  }
}
