package dcer.data

import dcer.serialization.CborSerializable
import edu.puc.core.execution.structures.output.{Match => JMatch}

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
  def apply(m: JMatch): Match = {
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
}
