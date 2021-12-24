package dcer.common.data

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import dcer.common.serialization.CborSerializable

import scala.concurrent.duration.{DurationInt, FiniteDuration}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(
      value = classOf[Predicate.None],
      name = "none"
    ),
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

  case class None() extends Predicate {
    override def toString: String = "None"
  }
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
      None(),
      Linear(),
      Quadratic(),
      Cubic()
    )

  def parse(str: String): Option[Predicate] = {
    // Here we use .startsWith because case classes .toString
    all.find(_.toString.toLowerCase.startsWith(str.toLowerCase))
  }

  /** Arbitrary await amount per event. This should be equivalent to executing
    * an access to a binary operation between fields.
    */
  val EventProcessingDuration: FiniteDuration = 10.millis

  /** Computes an hypothetical processing time for a given predicate and size of
    * complex event.
    *
    * In the future, this will be replace by an actual generic algorithm on top
    * of ComplexEvent.java that evaluates a predicate on the events in the CE.
    *
    * @param predicate
    *   Second-order predicate
    * @param complexEventSize
    *   Size of the complex event
    * @return
    */
  def predicateSimulationDuration(
      predicate: Predicate,
      complexEventSize: Long
  ): Option[FiniteDuration] =
    predicate match {
      case Predicate.None() =>
        Option.empty
      case nonNonePredicate =>
        val size: Double = complexEventSize.toDouble

        // Mock computational time depending on the complexity of the predicate
        val numberOfEventsToProcess: Long =
          (nonNonePredicate match {
            case Predicate.Linear() =>
              size
            case Predicate.Quadratic() =>
              scala.math.pow(size, 2)
            case Predicate.Cubic() =>
              scala.math.pow(size, 3)
            case Predicate.None() =>
              throw new RuntimeException(
                "This has already been pattern-matched before"
              )
          }).toLong /*safe cast*/

        Some(EventProcessingDuration * numberOfEventsToProcess)
    }
}
