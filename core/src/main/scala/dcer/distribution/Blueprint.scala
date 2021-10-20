package dcer.distribution

import dcer.data.{Event, Match}

import scala.collection.mutable.ListBuffer
import dcer.Implicits._
import dcer.data.Match.MaximalMatch

import scala.annotation.tailrec

/** Why an array? Efficiency.
  * @param value
  *   - Each position corresponds to an event type.
  *   - Each element corresponds to the 'k' in the k-combination of the kleene
  *     closure of that event type in the query.
  */
case class Blueprint(value: Array[Int]) extends AnyVal {
  // Given a maximal match, enumerates all matches in the maximal match
  // where the blueprint holds i.e. the sequences of each event type
  // are of the same size as the ones from the blueprint.
  def enumerate(maximalMatch: MaximalMatch): List[Match] = {
    type EventType = Int

    @tailrec
    def go(
        events: List[(Event, EventType)],
        previousEventType: EventType,
        kleene: Set[Event],
        acc: List[List[Event]]
    ): List[List[Event]] = {
      events match {
        case (event, eventType) :: tl if eventType == previousEventType =>
          go(tl, previousEventType, kleene + event, acc)

        case events =>
          val f = (k: Int) =>
            kleene.subsets(k).toList.flatMap { subset =>
              acc.map { events =>
                events ++ subset
              }
            }
          events match {
            case (event, eventType) :: tl =>
              val k = this.value(eventType - 1)
              go(tl, eventType, Set(event), f(k))

            case Nil => // Fin
              val k = this.value(previousEventType - 1)
              f(k)
          }
      }
    }

    val xs = maximalMatch.events.zip(maximalMatch.nodeList).toList
    val listOfEvents =
      go(xs.tail, xs.head._2, Set(xs.head._1), List(Nil))

    // We don't need to keep the nodeList anymore
    // NB: in the future we may want to compute the node list
    listOfEvents.map(events => Match(events.toArray, Array.empty))
  }
}

object Blueprint {

  type EventTypeSeqSize = Array[Int]

  /*
    1. Group by event type: {{A1}, {B1 B2}, {C1 C2 C3}, {D1}}
    2. Subsets sizes: {{1}, {1,2}, {1,2,3}, {1}}
    3. Cartesian product of subsets sizes to generate all configurations
   */
  def fromMaximalMatch(
      maximalMatch: Match
  ): (List[Blueprint], EventTypeSeqSize) = {
    // Step (1) and (2) in O(n)
    val subsetSizes: ListBuffer[List[Int]] = ListBuffer()
    val sizes: ListBuffer[Int] = ListBuffer()
    val (i, acc, _) = maximalMatch.nodeList.tail.foldLeft {
      (1, List(1), maximalMatch.nodeList.head)
    } {
      case ((i, acc, previousNode), node) if node == previousNode =>
        val new_i = i + 1
        (new_i, new_i :: acc, previousNode)
      case ((i, acc, _), node) =>
        subsetSizes += acc.reverse
        sizes += i
        (1, List(1), node)
    }
    subsetSizes += acc.reverse
    sizes += i

    (
      // Step (3)
      subsetSizes.toList.cartesianProduct.map(xs => Blueprint(xs.toArray)),
      // This corresponds to the size of each event type.
      sizes.toArray
    )
  }
}
