package dcer.unit

import dcer.core.data.Match.MaximalMatch
import dcer.core.data.{Event, IntValue, Match}
import dcer.core.distribution.Blueprint
import dcer.core.distribution.Blueprint.NumberOfMatches
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BlueprintSpec
    extends AnyFunSpec
    with Matchers
    with TestHelper
    with FromMaximalMatchTest
    with EnumerateTest
    with EnumerateDistinctTest {
  describe("Blueprint") {
    describe("Equality & Hash Code") {
      it("should use structural equality") {
        import dcer.common.Implicits._

        val bp1 = Blueprint(Array(1, 1, 1))
        val bp2 = Blueprint(Array(1, 1, 1))
        assert(bp1 == bp2)

        // Distinct uses hash equality
        val xs = List(bp1, bp2)
        assert(xs.distinct == List(bp1))

        // HashTrieMap uses
        val bp3 = Blueprint(Array(1, 1, 2))
        val bp4 = Blueprint(Array(1, 1, 1))
        val bp5 = Blueprint(Array(1, 1, 2))
        val blueprints = List((bp1, 1), (bp2, 2), (bp3, 3), (bp4, 4), (bp5, 5))
        val result = blueprints.foldLeft(Map.empty[Blueprint, List[Int]]) {
          case (acc, (blueprint, i)) =>
            acc.updatedWith(blueprint) {
              case None =>
                Some(List(i))
              case Some(ii) =>
                Some(i :: ii)
            }
        }
        val expected: Map[Blueprint, List[Int]] =
          Map(
            Blueprint(Array(1, 1, 1)) -> List(4, 2, 1),
            Blueprint(Array(1, 1, 2)) -> List(5, 3)
          )
        assert(result == expected)
      }
    }
    describe("fromMaximalMatch") {
      fromMaximalMatchTests.foreach {
        case Expectation(testName, maximalMatch, expected) =>
          it(testName) {
            val blueprints = Blueprint.fromMaximalMatch(maximalMatch)
            blueprints should have length expected.length.toLong
            blueprints should contain theSameElementsAs expected
          }
      }
    }
    describe("enumerate") {
      enumerateTests.foreach {
        case Expectation(testName, (blueprint, maximalMatch), expected) =>
          it(testName) {
            val result = blueprint.enumerate(maximalMatch)
            result should have length expected.length.toLong
            result should contain theSameElementsAs expected
          }
      }
    }
    describe("enumerateDistinct") {
      enumerateDistinctTests.foreach {
        case Expectation(
              testName,
              (blueprint, maximalMatches),
              (expected, expectedRepeated)
            ) =>
          it(testName) {
            val (result, resultRepeated) =
              blueprint.enumerateDistinct(maximalMatches)
            result should have length expected.length.toLong
            result should contain theSameElementsAs expected
            resultRepeated shouldBe expectedRepeated
          }
      }
    }
  }
}

trait FromMaximalMatchTest { self: TestHelper =>
  type FromMaximalMatchExpectation =
    Expectation[MaximalMatch, List[(Blueprint, NumberOfMatches)]]

  private val test1: FromMaximalMatchExpectation = {
    val stream = "ABBCCD"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, D1))
    val expectedResult =
      List(
        Blueprint(Array(1, 1, 1, 1)) -> 4L,
        Blueprint(Array(1, 1, 2, 1)) -> 2L,
        Blueprint(Array(1, 2, 1, 1)) -> 2L,
        Blueprint(Array(1, 2, 2, 1)) -> 1L
      )

    Expectation(
      testName = stream,
      input = maximalMatch,
      expectedResult
    )
  }

  private val test2: FromMaximalMatchExpectation = {
    val stream = "A"
    val maximalMatch = getMatch(Array(A1))
    val expectedResult =
      List(
        Blueprint(Array(1)) -> 1L
      )

    Expectation(
      testName = stream,
      input = maximalMatch,
      expectedResult
    )
  }

  private val test3: FromMaximalMatchExpectation = {
    val stream = "ABBCCCDD"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, C3, D1, D2))
    val expectedResult =
      List(
        Blueprint(Array(1, 1, 1, 1)) -> 12L,
        Blueprint(Array(1, 1, 1, 2)) -> 6L,
        Blueprint(Array(1, 1, 2, 1)) -> 12L,
        Blueprint(Array(1, 1, 2, 2)) -> 6L,
        Blueprint(Array(1, 1, 3, 1)) -> 4L,
        Blueprint(Array(1, 1, 3, 2)) -> 2L,
        Blueprint(Array(1, 2, 1, 1)) -> 6L,
        Blueprint(Array(1, 2, 1, 2)) -> 3L,
        Blueprint(Array(1, 2, 2, 1)) -> 6L,
        Blueprint(Array(1, 2, 2, 2)) -> 3L,
        Blueprint(Array(1, 2, 3, 1)) -> 2L,
        Blueprint(Array(1, 2, 3, 2)) -> 1L
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

  private val test2 = {
    val testName = "AAA (A = 1)"
    val maximalMatch = getMatch(Array(A1, A2, A3))
    val blueprint = Blueprint(Array(2))
    val expectedResult = List(
      Match(Array(A1, A2), Array.empty),
      Match(Array(A1, A3), Array.empty),
      Match(Array(A2, A3), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatch),
      expected = expectedResult
    )
  }

  private val test3 = {
    val testName = "ABBCCCD (A = 1, B = 2, C = 2, D = 1)"
    val maximalMatch = getMatch(Array(A1, B1, B2, C1, C2, C3, D1))
    val blueprint = Blueprint(Array(1, 2, 2, 1))
    val expectedResult = List(
      Match(Array(A1, B1, B2, C1, C2, D1), Array.empty),
      Match(Array(A1, B1, B2, C1, C3, D1), Array.empty),
      Match(Array(A1, B1, B2, C2, C3, D1), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatch),
      expected = expectedResult
    )
  }

  private val test4 = {
    val testName = "ABBBCCCDDDE (A = 1, B = 1, C = 2, D = 3, E = 1)"
    val maximalMatch = getMatch(
      Array(A1, B1, B2, B3, C1, C2, C3, D1, D2, D3, E1)
    )
    val blueprint = Blueprint(Array(1, 1, 2, 3, 1))
    val expectedResult = List(
      Match(Array(A1, B1, C1, C2, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B1, C1, C3, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B1, C2, C3, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B2, C1, C2, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B2, C1, C3, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B2, C2, C3, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B3, C1, C2, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B3, C1, C3, D1, D2, D3, E1), Array.empty),
      Match(Array(A1, B3, C2, C3, D1, D2, D3, E1), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatch),
      expected = expectedResult
    )
  }

  // Hey listen, don't forget to add the test here!
  val enumerateTests: List[EnumerateExpectation] =
    List(test1, test2, test3, test4)
}

trait EnumerateDistinctTest { self: TestHelper =>

  type EnumerateDistinctExpectation =
    Expectation[(Blueprint, List[MaximalMatch]), (List[Match], Int)]

  private val test1 = {
    val testName = "(A = 1): A1A2A3"
    val blueprint = Blueprint(Array(1))
    val maximalMatches = List(
      getMatch(Array(A1, A2, A3))
    )
    val expectedResult = List(
      Match(Array(A1), Array.empty),
      Match(Array(A2), Array.empty),
      Match(Array(A3), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatches),
      expected = (expectedResult, 0)
    )
  }

  private val test2 = {
    val testName = "(A = 1, B = 1): A1B1B2, A1A2B1"
    val blueprint = Blueprint(Array(1, 1))
    val maximalMatches = List(
      getMatch(Array(A1, B1, B2)),
      getMatch(Array(A1, A2, B1))
    )
    val expectedResult = List(
      Match(Array(A1, B1), Array.empty),
      Match(Array(A1, B2), Array.empty),
      Match(Array(A2, B1), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatches),
      expected = (expectedResult, 1)
    )
  }

  private val test3 = {
    val testName = "(A = 1, B = 2, C = 1): A1B1B2B3C1, A1A2B2B3C1"
    val blueprint = Blueprint(Array(1, 2, 1))
    val maximalMatches = List(
      getMatch(Array(A1, B1, B2, B3, C1)),
      getMatch(Array(A1, A2, B2, B3, C1))
    )
    val expectedResult = List(
      Match(Array(A1, B1, B2, C1), Array.empty),
      Match(Array(A1, B1, B3, C1), Array.empty),
      Match(Array(A1, B2, B3, C1), Array.empty),
      Match(Array(A2, B2, B3, C1), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatches),
      expected = (expectedResult, 1)
    )
  }

  private val test4 = {
    val testName =
      "(A = 1, B = 1, C = 1): A1B1C1, A1A2B1B2C1, A1A3B1B3C1, A1B1C1C2"
    val blueprint = Blueprint(Array(1, 1, 1))
    val maximalMatches = List(
      getMatch(Array(A1, B1, C1)),
      getMatch(Array(A1, A2, B1, B2, C1)),
      getMatch(Array(A1, A3, B1, B3, C1)),
      getMatch(Array(A1, B1, C1, C2))
    )
    val expectedResult = List(
      Match(Array(A1, B1, C1), Array.empty),
      Match(Array(A1, B2, C1), Array.empty),
      Match(Array(A2, B1, C1), Array.empty),
      Match(Array(A2, B2, C1), Array.empty),
      Match(Array(A1, B3, C1), Array.empty),
      Match(Array(A3, B1, C1), Array.empty),
      Match(Array(A3, B3, C1), Array.empty),
      Match(Array(A1, B1, C2), Array.empty)
    )

    Expectation(
      testName,
      input = (blueprint, maximalMatches),
      expected = (expectedResult, 3)
    )
  }

  // Hey listen, don't forget to add the test here!
  val enumerateDistinctTests: List[EnumerateDistinctExpectation] =
    List(test1, test2, test3, test4)
}

trait EventBuilder {
  val streamName = "S"

  private val A: Event = Event(name = "A", streamName)
  val A1: Event =
    Event(
      name = "A",
      streamName,
      index = 1,
      attributes = Map("id" -> IntValue(1))
    )
  val A2: Event =
    Event(
      name = "A",
      streamName,
      index = 2,
      attributes = Map("id" -> IntValue(2))
    )
  val A3: Event =
    Event(
      name = "A",
      streamName,
      index = 3,
      attributes = Map("id" -> IntValue(3))
    )

  private val B: Event = Event(name = "B", streamName)
  val B1: Event =
    Event(
      name = "B",
      streamName,
      index = 4,
      attributes = Map("id" -> IntValue(1))
    )
  val B2: Event =
    Event(
      name = "B",
      streamName,
      index = 5,
      attributes = Map("id" -> IntValue(2))
    )
  val B3: Event =
    Event(
      name = "B",
      streamName,
      index = 6,
      attributes = Map("id" -> IntValue(3))
    )

  private val C: Event = Event(name = "C", streamName)
  val C1: Event =
    Event(
      name = "C",
      streamName,
      index = 7,
      attributes = Map("id" -> IntValue(1))
    )
  val C2: Event =
    Event(
      name = "C",
      streamName,
      index = 8,
      attributes = Map("id" -> IntValue(2))
    )
  val C3: Event =
    Event(
      name = "C",
      streamName,
      index = 9,
      attributes = Map("id" -> IntValue(3))
    )

  private val D: Event = Event(name = "D", streamName)
  val D1: Event =
    Event(
      name = "D",
      streamName,
      index = 10,
      attributes = Map("id" -> IntValue(1))
    )
  val D2: Event =
    Event(
      name = "D",
      streamName,
      index = 11,
      attributes = Map("id" -> IntValue(2))
    )
  val D3: Event =
    Event(
      name = "D",
      streamName,
      index = 12,
      attributes = Map("id" -> IntValue(3))
    )

  private val E: Event = Event(name = "E", streamName)
  val E1: Event =
    Event(
      name = "E",
      streamName,
      index = 13,
      attributes = Map("id" -> IntValue(1))
    )
  val E2: Event =
    Event(
      name = "E",
      streamName,
      index = 14,
      attributes = Map("id" -> IntValue(2))
    )
  val E3: Event =
    Event(
      name = "E",
      streamName,
      index = 15,
      attributes = Map("id" -> IntValue(3))
    )

  val allEventTypes: Array[Event] = Array(A, B, C, D, E)

  def getMatch(events: Array[Event]): Match = {
    val names = allEventTypes.map(_.name)
    val nodeList = events.map(e => names.indexOf(e.name))
    Match(events, nodeList)
  }
}

trait TestHelper extends EventBuilder {
  case class Expectation[A, B](testName: String, input: A, expected: B)
}
