package dcer

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import dcer.data.{Port, Role}

import scala.util.{Failure, Success}
//import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

import scala.util.Try

/*
I would follow the `transformation` example since it is simpler than `stats` (we don't need the router).

CORE should start Worker(s) and wait until all of them are registered in the application.
(this number should be known in advance, otherwise, we can just wait until no more registries are received).

maximal MatchGroup should be distributed among workers. For the first iteration, use a round-robin distribution

- [ ] (optional) Singleton instance of CORE Actor
- [ ] Limit the minimum number of actors to `n`.
 */

object App {

  val seedPort: Port = {
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
          case None       => throw new RuntimeException(s"Invalid port: $rawPort")
          case Some(port) => port
        }
      case Failure(ex) =>
        throw new RuntimeException(
          s"Failed to parse $seedNodeRaw: ${ex.toString}"
        )
    }
  }

  /** Akka will pick an available port for you. */
  val atRandom: Port = Port.unsafePort(0)

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      if (cluster.selfMember.hasRole(data.Worker.toString)) {
        val workersPerNode =
          ctx.system.settings.config.getInt("my-config.workers-per-node")
        (1 to workersPerNode).foreach { n =>
          ctx.spawn(actors.Worker(), s"Worker-$n")
        }
      }

      if (cluster.selfMember.hasRole(data.Engine.toString)) {
        val query0Path: String = "./src/main/resources/query_0"
        ctx.spawn(actors.EngineManager(query0Path), "EngineManager")
      }

      Behaviors.empty
    }
  }

  def startup(role: Role, port: Port): Unit = {
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=${port.port}
        akka.cluster.roles = [${role.toString}]
        """)
      .withFallback(ConfigFactory.load())

    val _ = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(data.Engine, seedPort)
      startup(data.Worker, atRandom)
      startup(data.Worker, atRandom)
    } else {
      val usageMsg: String = "Usage: <role> <port>"

      val parsedArgs = for {
        rawRole <- Try(args(0)).toOption
        role <- Role.parse(rawRole)
        rawPort <- Try(args(1)).toOption
        port <- Port.parse(rawPort)
      } yield (role, port)

      parsedArgs match {
        case None =>
          println(usageMsg)
          System.exit(-1)
        case Some((role, port)) =>
          startup(role, port)
      }
    }
  }
}
