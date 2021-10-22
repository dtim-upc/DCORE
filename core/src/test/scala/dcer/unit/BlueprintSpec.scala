package dcer.unit

import dcer.data.Match.MaximalMatch
import dcer.data.{Event, IntValue, Match}
import dcer.distribution.Blueprint
import dcer.distribution.Blueprint.EventTypeSeqSize
import org.scalactic.Equality
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BlueprintSpec extends AnyFunSpec with AllTest {
  describe("Blueprint") {
    describe("fromMaximalMatch") {
      fromMaximalMatchTests.foreach {
        case Expectation(
              testName,
              maximalMatch,
              (expectedBlueprints, expectedSize)
            ) =>
          it(testName) {
            val (blueprints, sizes) = Blueprint.fromMaximalMatch(maximalMatch)
            blueprints ~ expectedBlueprints
            sizes ~ expectedSize
          }
      }
    }
    describe("enumerate") {
      enumerateTests.foreach {
        case Expectation(testName, (blueprint, maximalMatch), expected) =>
          it(testName) {
            val result = blueprint.enumerate(maximalMatch)
            // TODO compare List of Matches
          }
      }
    }
  }
}

trait AllTest extends TestHelper with FromMaximalMatchTest with EnumerateTest

trait FromMaximalMatchTest { self: TestHelper =>
  type FromMaximalMatchExpectation =
    Expectation[MaximalMatch, (List[Blueprint], EventTypeSeqSize)]

  private val test1 = {
    val stream = "ABBCCD"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, D1))
    val expectedResult = (
      List(
        Blueprint(Array(1, 1, 1, 1)),
        Blueprint(Array(1, 1, 2, 1)),
        Blueprint(Array(1, 2, 1, 1)),
        Blueprint(Array(1, 2, 2, 1))
      ),
      Array(1, 2, 2, 1)
    )
    Expectation(
      testName = stream,
      input = maximalMatch,
      expectedResult
    )
  }

  private val test2 = {
    val stream = "A"
    val maximalMatch = getMatch(Array(A1))
    val expectedResult = (
      List(
        Blueprint(Array(1))
      ),
      Array(1)
    )
    Expectation(
      testName = stream,
      input = maximalMatch,
      expectedResult
    )
  }

  private val test3 = {
    val stream = "ABBCCCDD"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, C3, D1, D2))
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
    Expectation(
      testName = stream,
      input = maximalMatch,
      expectedResult
    )
  }

  // Hey listen, don't forget to add the test here!
  val fromMaximalMatchTests: List[FromMaximalMatchExpectation] =
    List(test1, test2, test3)
}

trait EnumerateTest { self: TestHelper =>

  type EnumerateExpectation =
    Expectation[(Blueprint, MaximalMatch), List[Match]]

  private val test1 = {
    val testName = "ABBCCD (A = 1, B = 1, C = 1, D = 1)"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, D1))
    val blueprint = Blueprint(Array(1, 1, 1, 1))
    val expectedResult = List(
      Match(Array(A1, B1, C1, D1), Array.empty),
      Match(Array(A1, B1, C2, D1), Array.empty),
      Match(Array(A1, B2, C1, D1), Array.empty),
      Match(Array(A1, B2, C2, D1), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatch),
      expected = expectedResult
    )
  }

  // Hey listen, don't forget to add the test here!
  val enumerateTests: List[EnumerateExpectation] =
    List(test1)
}

trait EventBuilder {
  val streamName = "S"

  private val A: Event = Event(name = "A", streamName)
  val A1: Event =
    Event(name = "A", streamName, attributes = Map("id" -> IntValue(1)))
  val A2: Event =
    Event(name = "A", streamName, attributes = Map("id" -> IntValue(2)))
  val A3: Event =
    Event(name = "A", streamName, attributes = Map("id" -> IntValue(3)))

  private val B: Event = Event(name = "B", streamName)
  val B1: Event =
    Event(name = "B", streamName, attributes = Map("id" -> IntValue(1)))
  val B2: Event =
    Event(name = "B", streamName, attributes = Map("id" -> IntValue(2)))
  val B3: Event =
    Event(name = "B", streamName, attributes = Map("id" -> IntValue(3)))

  private val C: Event = Event(name = "C", streamName)
  val C1: Event =
    Event(name = "C", streamName, attributes = Map("id" -> IntValue(1)))
  val C2: Event =
    Event(name = "C", streamName, attributes = Map("id" -> IntValue(2)))
  val C3: Event =
    Event(name = "C", streamName, attributes = Map("id" -> IntValue(3)))

  private val D: Event = Event(name = "D", streamName)
  val D1: Event =
    Event(name = "D", streamName, attributes = Map("id" -> IntValue(1)))
  val D2: Event =
    Event(name = "D", streamName, attributes = Map("id" -> IntValue(2)))
  val D3: Event =
    Event(name = "D", streamName, attributes = Map("id" -> IntValue(3)))

  private val E: Event = Event(name = "E", streamName)
  val E1: Event =
    Event(name = "E", streamName, attributes = Map("id" -> IntValue(1)))
  val E2: Event =
    Event(name = "E", streamName, attributes = Map("id" -> IntValue(2)))
  val E3: Event =
    Event(name = "E", streamName, attributes = Map("id" -> IntValue(3)))

  val allEventTypes: Array[Event] = Array(A, B, C, D, E)

  def getMatch(events: Array[Event]): Match = {
    val names = allEventTypes.map(_.name)
    val nodeList = events.map(e => names.indexOf(e.name))
    Match(events, nodeList)
  }
}

trait TestHelper extends EventBuilder with Matchers {
  case class Expectation[A, B](testName: String, input: A, expected: B)

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

  implicit val blueprintEquality: Equality[Blueprint] =
    new Equality[Blueprint] {
      override def areEqual(a: Blueprint, b: Any): Boolean = true
    }

  implicit class ListBlueprint(v: List[Blueprint]) {
    def ~(other: List[Blueprint]): Assertion = {
      // We need to unwrap the Blueprint so `contain` can use the Equality[Array[Int]]
//      val v1 = v.map(_.value)
//      val v2 = other.map(_.value)
//      v1 should contain theSameElementsAs v2
      v should contain theSameElementsAs other
    }
  }

//  implicit class ListMatch(v: List[Match]) {
//    def ~(other: List[Match]): Assertion = {
//      // We need to unwrap the Blueprint so `contain` can use the Equality[Array[Int]]
//      val v1 = v.map(_.value)
//      val v2 = other.map(_.value)
//      v1 should contain theSameElementsAs v2
//    }
//  }
}
