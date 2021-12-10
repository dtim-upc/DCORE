package dcer.distribution

import dcer.Binomial
import dcer.data.{DAG, Event, Match}

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
case class Blueprint(value: Array[Int]) {
  type EventType = Int

  def pretty: String = {
    s"Blueprint(${this.value.mkString(",")})"
  }

  // Array.equals is defined as referential equality.
  // By default, value classes reuses underlying values' impls.
  // We re-define equality to be structural instead.
  // We sacrifice performance gains (zero allocation) from AnyVal.
  override def equals(obj: Any): Boolean = {
    obj match {
      case obj: Blueprint => this.value.sameElements(obj.value)
      case _              => false
    }
  }
  // We also need to override hashcode since some data structures
  // e.g. HashTrieMap and HashSet uses hashes to compute equality
  override def hashCode(): Int = {
    scala.util.hashing.MurmurHash3.arrayHash(this.value)
  }

  // Given a maximal match, enumerates all matches in the maximal match
  // where the blueprint holds i.e. the sequences of each event type
  // are of the same size as the ones from the blueprint.
  def enumerate(maximalMatch: MaximalMatch): List[Match] = {
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

        case events => {
          val newAcc = {
            val k = this.value(previousEventType)
            kleene.subsets(k).toList.flatMap { subset =>
              acc.map { events =>
                events ++ subset
              }
            }
          }

          events match {
            // eventType1 /= previousEventType
            case (event, newEventType) :: tl =>
              go(
                tl,
                newEventType,
                Set(event),
                newAcc
              )

            case Nil => // Fin
              newAcc
          }
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

  // This is part of the algorithm MaximalMatchesDisjointEnumeration.
  // The algorithm is similar to enumerate but does not generates duplicates
  // by using an auxiliary data structure to keep track of the matches found.
  def enumerateDistinct(
      maximalMatches: List[MaximalMatch]
  ): (List[Match], Int /*repeated matches*/ ) = {
    val buffer = ListBuffer.empty[Match]
    var repeated: Int = 0

    def go(
        events: List[(Event, EventType)], // Remaining events
        previousEventType: EventType,
        kleene: Set[Event], // Accumulates consecutive events of the same type
        acc: List[Event], // Future Match
        node: DAG[Event],
        isNew: Boolean // We will only output if isNew
    ): Unit = {
      // This will iterate over the k-combination set.
      // On each combination, it will execute forEachCombination callback.
      def inner(
          forEachCombination: (List[Event], DAG[Event], Boolean) => Unit
      ): Unit = {
        val k = this.value(previousEventType)
        kleene.subsets(k).foreach { events =>
          var next = node
          var nextIsNew = isNew
          // Checks if the events were already present in the DAG.
          // Otherwise, it will add them.
          events.foreach { e =>
            next.edges.find(_.root.index == e.index) match {
              case Some(n) =>
                next = n
              case None =>
                nextIsNew = true
                val n = DAG.single(e)
                next.edges += n
                next = n
            }
          }
          forEachCombination(acc ++ events.toList, next, nextIsNew)
        }
      }

      events match {
        // Base case
        case Nil =>
          inner { (events, _, isNew) =>
            if (isNew) {
              buffer += Match(events.toArray, Array.empty)
            } else {
              repeated += 1
            }
          }

        // Recursive case
        case (event, eventType) :: tl =>
          if (eventType == previousEventType) {
            go(tl, previousEventType, kleene + event, acc, node, isNew)
          } else {
            inner { (nextAcc, nextNode, nextIsNew) =>
              go(tl, eventType, Set(event), nextAcc, nextNode, nextIsNew)
            }
          }
      }
    }

    // The DAG will be kept during different maximal matches.
    // This will prevent us from repeating outputs.
    val root: DAG[Event] = DAG.single(root = null)

    maximalMatches.foreach { maximalMatch =>
      val ((firstEvent, firstEventType) :: events) =
        maximalMatch.events.zip(maximalMatch.nodeList).toList
      go(events, firstEventType, Set(firstEvent), Nil, root, isNew = false)
    }

    (buffer.toList, repeated)
  }
}

object Blueprint {
  type EventTypeSeqSize = Array[Int]
  type NumberOfMatches = Long

  /*
    1. Group by event type: {{A1}, {B1 B2}, {C1 C2 C3}, {D1}}
    2. Subsets sizes: {{1}, {1,2}, {1,2,3}, {1}}
    3. Cartesian product of subsets sizes to generate all configurations
   */
  def fromMaximalMatch(
      maximalMatch: Match
  ): List[(Blueprint, NumberOfMatches)] = {
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

    val sizesList = sizes.toList

    // Step (3)
    subsetSizes.toList.cartesianProduct.map { xs =>
      val numberOfMatches =
        xs.zip(sizesList)
          .map { case (k, n) =>
            Binomial.binomialUnsafe(n, k)
          }
          .product
      val blueprint = Blueprint(xs.toArray)
      (blueprint, numberOfMatches)
    }
  }
}
