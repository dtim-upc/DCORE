package dcer

import akka.actor.typed.ActorSystem
import cats.implicits._
import com.monovore.decline._
import com.typesafe.config.ConfigFactory
import dcer.StartUp.startup
import dcer.actors.Root
import dcer.data.{Callback, DistributionStrategy, Port, QueryPath, Role}
import dcer.distribution.Predicate

import java.nio.file.Path

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

          val queryPathOpt =
            Opts
              .option[Path](
                "query",
                help = s"Examples at './core/src/main/resources/'"
              )
              .mapValidated { path =>
                QueryPath(path).toValidNel(s"Invalid query path: $path")
              }
              .orNone

          val strategyOpt =
            Opts
              .option[String](
                "strategy",
                help = s"Available strategies: ${DistributionStrategy.all}"
              )
              .mapValidated { str =>
                DistributionStrategy
                  .parse(str)
                  .toValidNel(s"Invalid strategy: $str")
              }
              .orNone

          (roleOpt, portOpt, queryPathOpt, strategyOpt).mapN {
            (role, portOpt, queryPath, strategy) =>
              val port = portOpt match {
                case None =>
                  role match {
                    case data.Engine => Port.SeedPort
                    case data.Worker => Port.RandomPort
                  }
                case Some(port) => port
              }
              startup(role, port, queryPath, callback = None, strategy)
          }
        }

        demo <+> run
      }
    )

object StartUp {
  def startup(
      role: Role,
      port: Port,
      queryPath: Option[QueryPath] = None,
      callback: Option[Callback] = None,
      strategy: Option[DistributionStrategy] = None,
      predicate: Option[Predicate] = None
  ): Unit = {
    def optional[V](
        key: String,
        value: Option[V],
        show: V => String
    ): String =
      value.map(v => s"$key = ${show(v)}").getOrElse("")

    val queryOption = optional(
      key = "dcer.query-path",
      value = queryPath,
      show = (_: QueryPath).value.toString
    )
    val strategyOption = optional(
      key = "dcer.distribution-strategy",
      value = strategy,
      show = (_: DistributionStrategy).toString
    )

    val predicateOption = optional(
      key = "dcer.second-order-predicate",
      value = predicate,
      show = (_: Predicate).toString
    )

    val config =
      ConfigFactory
        .parseString(
          s"""akka.remote.artery.canonical.port=${port.port}
             |akka.cluster.roles = [${role.toString}]
             |$queryOption
             |$strategyOption
             |$predicateOption
             |""".stripMargin
        )
        .withFallback(ConfigFactory.load())

    val _ = ActorSystem(Root(callback), "ClusterSystem", config)
  }
}
