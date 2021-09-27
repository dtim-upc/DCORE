package dcer

import akka.actor.typed.ActorSystem
import cats.implicits._
import com.monovore.decline._
import com.typesafe.config.ConfigFactory
import dcer.App.startup
import dcer.actors.Root
import dcer.data.{Port, Role}

/**  For a demo:
  *    Machine 1: $ sbt "run --demo"
  *
  *  To run on multiple machines:
  *    Machine 1: $ sbt "run --role engine"
  *    Machine 2: $ sbt "run --role worker"
  *    (Optional) Machine 3: $ sbt "run --role worker"
  *    (Optional) ...
  *
  *   Change the configuration at 'src/main/resources/application.conf'.
  */
object App
    extends CommandApp(
      name = "dcer",
      header = "Distributed CER",
      main = {
        val demo =
          Opts.flag("demo", help = "Run the demo").orFalse

        val roleOpt =
          Opts
            .option[String]("role", help = s"Available roles: ${Role.all}")
            .mapValidated { r =>
              Role.parse(r).toValidNel(s"Invalid role: $r")
            }

        val portOpt =
          Opts
            .option[String]("port", help = s"Available ports: [1024, 49152]")
            .mapValidated { p =>
              Port.parse(p).toValidNel(s"Invalid port: $p")
            }
            .orNone

        (demo, roleOpt, portOpt).mapN { (demo, role, portOpt) =>
          if (demo) {
            startup(data.Engine, Port.SeedPort)
            startup(data.Worker, Port.RandomPort)
            startup(data.Worker, Port.RandomPort)
          } else {
            val port = portOpt match {
              case None =>
                role match {
                  case data.Engine => Port.SeedPort
                  case data.Worker => Port.RandomPort
                }
              case Some(port) => port
            }
            startup(role, port)
          }
        }
      }
    )
    with AkkaSystem

trait AkkaSystem {
  def startup(
      role: Role,
      port: Port
  ): Unit = {
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=${port.port}
        akka.cluster.roles = [${role.toString}]
        """)
      .withFallback(ConfigFactory.load())

    val _ = ActorSystem(Root(), "ClusterSystem", config)
  }
}
