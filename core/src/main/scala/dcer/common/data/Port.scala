package dcer.common.data

import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success, Try}

// https://gist.github.com/tpolecat/a5cb0dc9adeacc93f846835ed21c92d2
sealed abstract case class Port(port: Int)

object Port {

  /** Akka will pick an available port for you. */
  final val RandomPort: Port = Port.unsafePort(0)

  final val SeedPort: Port = {
    val config = ConfigFactory.load()

    val seedNodesRaw = config.getStringList("akka.cluster.seed-nodes")
    assert(
      seedNodesRaw.size() == 1,
      "Modify logic in seedPort to handle multiple seed ports"
    )
    val seedNodeRaw = seedNodesRaw.get(0)

    // Example: "akka://ClusterSystem@127.0.0.1:25251"
    Try(seedNodeRaw.split('@')(1).split(':')(1).toInt) match {
      case Success(rawPort) =>
        Port.parse(rawPort) match {
          case None => throw new RuntimeException(s"Invalid port: $rawPort")
          case Some(port) => port
        }
      case Failure(ex) =>
        throw new RuntimeException(
          s"Failed to parse $seedNodeRaw: ${ex.toString}"
        )
    }
  }

  // Use at your own risk.
  private def unsafePort(rawPort: Int): Port =
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
