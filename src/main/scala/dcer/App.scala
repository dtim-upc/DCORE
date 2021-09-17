package dcer

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import dcer.actors.Root
import dcer.data.{Port, Role}
import scala.util.Try

object App {
  /*
  For a demo: $ sbt run
  To run on multiple machines, run each on a different machine (also works on multiple terminals):
   - $ sbt "run Engine"
   - $ sbt "run Worker"
   - $ sbt "run Worker"
   - $ sbt "run Worker"
   */
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(data.Engine, Port.SeedPort)
      startup(data.Worker, Port.RandomPort)
      startup(data.Worker, Port.RandomPort)
    } else {
      val parsedArgs = for {
        role <- (
          for {
            rawRole <- Try(args(0)).toOption
            role <- Role.parse(rawRole)
          } yield role
        ).orElse(Some(data.Worker))
        port <- (for {
          rawPort <- Try(args(1)).toOption
          port <- Port.parse(rawPort)
        } yield port)
          .orElse(role match {
            case data.Engine => Some(Port.SeedPort)
            case data.Worker => Some(Port.RandomPort)
          })
      } yield (role, port)

      parsedArgs match {
        case None =>
          val usageMsg: String = "Usage: <role> <port>"
          println(usageMsg)
          System.exit(-1)
        case Some((role, port)) =>
          startup(role, port)
      }
    }
  }

  def startup(role: Role, port: Port): Unit = {
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=${port.port}
        akka.cluster.roles = [${role.toString}]
        """)
      .withFallback(ConfigFactory.load())

    val _ = ActorSystem(Root(), "ClusterSystem", config)
  }
}
