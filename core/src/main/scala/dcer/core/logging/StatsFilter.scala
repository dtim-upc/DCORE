package dcer.core.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.{Marker, MarkerFactory}

// This class should only be used in `logback.xml`.

class StatsFilter extends Filter[ILoggingEvent] {
  override def decide(event: ILoggingEvent): FilterReply = {
    Option(event.getMarker) match {
      case Some(marker) =>
        if (marker == StatsFilter.marker) {
          FilterReply.ACCEPT
        } else {
          FilterReply.DENY
        }
      case None => FilterReply.DENY
    }
  }
}

object StatsFilter {
  val marker: Marker = MarkerFactory.getMarker("STATS")
}
