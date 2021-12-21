package dcer.common.data

import scala.concurrent.duration._

case class Timer(startup: Long) {
  def elapsedTime(): FiniteDuration = {
    val elapsed = System.nanoTime() - startup
    (elapsed / 1e9).seconds
  }
}

case object Timer {
  def apply(): Timer = {
    Timer(startup = System.nanoTime())
  }
}
