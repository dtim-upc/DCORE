package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match => CoreMatch}

import scala.collection.JavaConverters._ // .asScala

/*
The java Match is just an iterator over MatchNode.

We tried to simplify the class since DistributedCer is only using
the nodeList of the match.
 */

//case class MatchNode(event: Event, index: Long, node: Int)
//    extends CborSerializable

case class Match(events: List[Event], nodeList: List[Int])
    extends CborSerializable

object Match {
  def apply(m: CoreMatch): Match = {
    val events: List[Event] = m.iterator().asScala.toList.map(Event(_))
    val nodeList: List[Int] = m.getNodeList.asScala.toList.map(_.toInt)
    Match(events, nodeList)
  }

  /** Pretty print a match as a list of events. */
  def pretty(m: Match): String = {
    val builder = new StringBuilder("")
    m.events.foreach { event =>
      builder ++= event.toString
      builder += '\n'
    }
    builder.result()
  }

  /** Computes the weight of a match.
    *
    * Def. (Weight). The weight of a match is computed by the sum of the
    * factorial of the length of all kleene plus in the event sequence of the
    * match.
    */
  def weight(m: Match): Long = {
    def kleeneWeight(length: Int): Long =
      scala.math.pow(2, length.toDouble).toLong - 1

    // NB: assuming there is at least one element
    val (weight, (count, _)) = m.nodeList.tail.foldLeft {
      (0L, (1, m.nodeList.head))
    } {
      case ((weight, (count, previousNode)), node) if node == previousNode =>
        (weight, (count + 1, previousNode))
      case ((weight, (count, _)), node) =>
        (weight + kleeneWeight(count), (1, node))
    }

    // Handle last kleene sequence
    weight + kleeneWeight(count)
  }
}
