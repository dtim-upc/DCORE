package dcer.unit

import dcer.data.Match.MaximalMatch
import dcer.data.{Event, Match}
import dcer.distribution.Blueprint
import dcer.distribution.Blueprint.EventTypeSeqSize
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BlueprintSpec
    extends AnyFunSpec
    with Matchers
    with EventBuilder
    with FromMaximalMatchTest
    with EnumerateTest {

  describe("Blueprint") {
    describe("fromMaximalMatch") {
      fromMaximalMatchTests.foreach {
        case (stream, maximalMatch, (expectedBlueprints, expectedSize)) =>
          it(s"should generate all blueprints from $stream") {
            val (blueprints, sizes) = Blueprint.fromMaximalMatch(maximalMatch)
            blueprints ~ expectedBlueprints
            sizes ~ expectedSize
          }
      }
    }

    describe("enumerate") {
      it(s"TODO") {
        // TODO
      }
    }
  }
}

trait EventBuilder {
  val streamName = "S"
  val A: Event = Event(name = "A", streamName)
  val B: Event = Event(name = "B", streamName)
  val C: Event = Event(name = "C", streamName)
  val D: Event = Event(name = "D", streamName)
  val E: Event = Event(name = "E", streamName)
  val allEvents: Array[Event] = Array(A, B, C, D, E)

  def getMatch(events: Array[Event]): Match = {
    val nodeList = events.map(allEvents.indexOf(_))
    Match(events, nodeList)
  }
}

trait FromMaximalMatchTest { self: EventBuilder with Matchers =>
  private val test1 = {
    val stream = "ABBCCD"
    val maximalMatch = getMatch(Array(A, B, B, C, C, D))
    val expectedResult = (
      List(
        Blueprint(Array(1, 1, 1, 1)),
        Blueprint(Array(1, 1, 2, 1)),
        Blueprint(Array(1, 2, 1, 1)),
        Blueprint(Array(1, 2, 2, 1))
      ),
      Array(1, 2, 2, 1)
    )
    (stream, maximalMatch, expectedResult)
  }

  private val test2 = {
    val stream = "A"
    val maximalMatch = getMatch(Array(A))
    val expectedResult = (
      List(
        Blueprint(Array(1))
      ),
      Array(1)
    )
    (stream, maximalMatch, expectedResult)
  }

  private val test3 = {
    val stream = "ABBCCCDD"
    val maximalMatch = getMatch(Array(A, B, B, C, C, C, D, D))
    val expectedResult = (
      List(
        Blueprint(Array(1, 1, 1, 1)),
        Blueprint(Array(1, 1, 1, 2)),
        Blueprint(Array(1, 1, 2, 1)),
        Blueprint(Array(1, 1, 2, 2)),
        Blueprint(Array(1, 1, 3, 1)),
        Blueprint(Array(1, 1, 3, 2)),
        Blueprint(Array(1, 2, 1, 1)),
        Blueprint(Array(1, 2, 1, 2)),
        Blueprint(Array(1, 2, 2, 1)),
        Blueprint(Array(1, 2, 2, 2)),
        Blueprint(Array(1, 2, 3, 1)),
        Blueprint(Array(1, 2, 3, 2))
      ),
      Array(1, 2, 3, 2)
    )
    (stream, maximalMatch, expectedResult)
  }

  // Hey listen, don't forget to add the test here!
  val fromMaximalMatchTests
      : List[(String, MaximalMatch, (List[Blueprint], EventTypeSeqSize))] =
    List(test1, test2, test3)

  // What's the problem?
  //
  // Array's equals method compares object identity:
  //   assert(Array(1,2) == Array(1,2)) // false
  //
  // Equality[Array[Int]] compares the two arrays structurally,
  // taking into consideration the equality of the array's contents:
  implicit class ArrayOps[T](v: Array[T]) {
    def ~(other: Array[T]): Assertion = {
      v should equal(other)
    }
  }

  implicit class ListBlueprint(v: List[Blueprint]) {
    def ~(other: List[Blueprint]): Assertion = {
      // We need to unwrap the Blueprint so `contain` can use the Equality[Array[Int]]
      val v1 = v.map(_.value)
      val v2 = other.map(_.value)
      v1 should contain theSameElementsAs v2
    }
  }
}

trait EnumerateTest {
  self: EventBuilder with Matchers =>
  private val test1 = {
    val stream = "ABBCCD"
    val maximalMatch = getMatch(Array(A, B, B, C, C, D))
    val expectedResult = (
      List(
        Blueprint(Array(1, 1, 1, 1)),
        Blueprint(Array(1, 1, 2, 1)),
        Blueprint(Array(1, 2, 1, 1)),
        Blueprint(Array(1, 2, 2, 1))
      ),
      Array(1, 2, 2, 1)
    )
    (stream, maximalMatch, expectedResult)
  }

  // Hey listen, don't forget to add the test here!
  val fromMaximalMatchTests
      : List[(String, MaximalMatch, (List[Blueprint], EventTypeSeqSize))] =
    List(test1, test2, test3)
}
