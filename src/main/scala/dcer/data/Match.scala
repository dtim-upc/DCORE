package dcer.data

import edu.puc.core.execution.structures.output.{Match => JMatch}
import scala.collection.JavaConverters._ // .asScala

object Match {

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
