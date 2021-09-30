package dcer

import akka.actor.typed.ActorSystem
import cats.implicits._
import com.monovore.decline._
import com.typesafe.config.ConfigFactory
import dcer.StartUp.startup
import dcer.actors.Root
import dcer.data.{Port, Role}

// TODO
// - [ ] Benchmarks: use jmh and implement something like in FlinkCore_test.java i.e. an incremental kleene star to see that time increases exponentially.
// - [ ] Sequential strategy: only 1 worker
// - [ ] Read paper about double hasing and implement it
// - [ ] Serialization of MatchGrouping

/*
The 'worker's are stopping gracefully with the same code.
But, the 'engine' process does not stop, you have to kill it.

var system: ActorSystem[ActorTerminated] = null
val terminate = () => { system.terminate() }
system = ActorSystem(Root(terminate), "ClusterSystem", config)

I have tried calling terminate but this doesn't work.
Although, calling system.terminate() right after the start works.

There is probably a security mechanism that prevents the seed node from
stopping after some workers have been connected.
 */
object App
    extends CommandApp(
      name = "dcer",
      header = "A distributed complex event processing engine.",
      main = {
        val demo = {
          val demo = Opts.flag("demo", help = "Run the demo")
          demo.map { _ =>
            startup(data.Engine, Port.SeedPort)
            startup(data.Worker, Port.RandomPort)
            startup(data.Worker, Port.RandomPort)
          }
        }

        val run = {
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

          (roleOpt, portOpt).mapN { (role, portOpt) =>
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

        demo <+> run
      }
    )

object StartUp {
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
