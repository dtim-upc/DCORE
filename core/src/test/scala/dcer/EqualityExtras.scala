package dcer

import dcer.data.{Event, Match}
import dcer.distribution.Blueprint
import org.scalactic.Equality

/* How to use:

class MySpec extends Matchers with EqualityExtras
 */
trait EqualityExtras {
  // What's the problem?
  //
  // Array's equals method compares object identity:
  //   assert(Array(1,2) == Array(1,2)) // false
  //
  // Equality[Array[Int]] compares the two arrays structurally,
  // taking into consideration the equality of the array's contents.

  implicit val blueprintEquality: Equality[Blueprint] =
    new Equality[Blueprint] {
      override def areEqual(a: Blueprint, b: Any): Boolean =
        b match {
          case b: Blueprint =>
            implicitly[Equality[Array[Int]]].areEqual(a.value, b.value)
          case _ => false
        }
    }

  implicit val matchEquality: Equality[Match] =
    new Equality[Match] {
      override def areEqual(a: Match, b: Any): Boolean = {
        b match {
          case b: Match =>
            implicitly[Equality[Array[Event]]].areEqual(a.events, b.events) &&
              implicitly[Equality[Array[Int]]].areEqual(a.nodeList, b.nodeList)
          case _ => false
        }
      }
    }
}
