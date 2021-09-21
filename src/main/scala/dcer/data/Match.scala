package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match => JMatch}

import scala.collection.JavaConverters._ // .asScala

/*
The java Match is just an iterator over MatchNode.

We tried to simplify the class since DistributedCer is only using
the nodeList of the match.
 */
case class MatchNode(event: Event, index: Long, node: Int)
    extends CborSerializable
case class Match(nodeList: Array[MatchNode]) extends CborSerializable
object Match {
  def apply(m: JMatch): Match = {
    // TODO
    ???
  }

  /** Pretty print a match as a list of events. */
  def pretty(m: JMatch): String = {
    val builder = new StringBuilder("")
    m.iterator().asScala.toList.foreach { event =>
      builder ++= event.toString
      builder += '\n'
    }
    builder.result()
  }
}
