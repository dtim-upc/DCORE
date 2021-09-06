package dcer.data

import scala.util.Try

// https://gist.github.com/tpolecat/a5cb0dc9adeacc93f846835ed21c92d2
sealed abstract case class Port(port: Int)

object Port {
  // Use at your own risk.
  def unsafePort(rawPort: Int): Port =
    new Port(rawPort) {}

  def parse(str: String): Option[Port] =
    for {
      n <- Try(str.toInt).toOption
      port <- parse(n)
    } yield port

  def parse(rawPort: Int): Option[Port] = {
    rawPort match {
      case p if p >= 1024 && p < 49152 => Some(new Port(p) {})
      case _                           => None
    }
  }
}
