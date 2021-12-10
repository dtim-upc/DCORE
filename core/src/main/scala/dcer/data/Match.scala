package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match => CoreMatch}

import scala.collection.JavaConverters._ // .asScala

// nodeList starts at index 0
case class Match(events: Array[Event], nodeList: Array[Int])
    extends CborSerializable {
  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Match =>
        other.events.sameElements(this.events) &&
          other.nodeList.sameElements(this.nodeList)
      case _ => false
    }
  }

  // Is this ok?
  override def hashCode(): Int = {
    scala.util.hashing.MurmurHash3.arrayHash(this.events)
  }
}

object Match {
  type MaximalMatch = Match

  def apply(m: CoreMatch): Match = {
    val events: Array[Event] = m.iterator().asScala.toArray.map(Event(_))
    val nodeList: Array[Int] = m.getNodeList.asScala.toArray.map(_.toInt)
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
