package dcer.distribution

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import dcer.serialization.CborSerializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(
      value = classOf[Predicate.Linear],
      name = "linear"
    ),
    new JsonSubTypes.Type(
      value = classOf[Predicate.Quadratic],
      name = "quadratic"
    ),
    new JsonSubTypes.Type(
      value = classOf[Predicate.Cubic],
      name = "cubic"
    )
  )
)
sealed trait Predicate extends CborSerializable

object Predicate {
  // These should be case objects but serialization doesn't work out
  // of the box for case objects (see https://doc.akka.io/docs/akka/current/serialization-jackson.html#adt-with-trait-and-case-object)
  //
  // This is why we decided to use case classes with no parameters.
  case class Linear() extends Predicate {
    override def toString: String = "Linear"
  }
  case class Quadratic() extends Predicate {
    override def toString: String = "Quadratic"
  }
  case class Cubic() extends Predicate {
    override def toString: String = "Cubic"
  }

  // Don't forget to update this
  val all: List[Predicate] =
    List(
      Linear(),
      Quadratic(),
      Cubic()
    )

  def parse(str: String): Option[Predicate] = {
    // Here we use .startsWith because case classes .toString
    all.find(_.toString.toLowerCase.startsWith(str.toLowerCase))
  }
}
