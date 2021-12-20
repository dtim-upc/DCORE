package dcer

import akka.actor.typed.{ActorSystem, Behavior}
import cats.implicits._
import com.monovore.decline._
import com.typesafe.config.{Config, ConfigFactory}
import dcer.common.actors.Root
import dcer.common.data.{
  Callback,
  Master,
  Port,
  Predicate,
  QueryPath,
  Role,
  Slave
}
import dcer.core.data.DistributionStrategy

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration

object App
    extends CommandApp(
      name = "dcer",
      header = "A distributed complex event processing engine.",
      main = {
        // Uses CORE as a dependency.
        val coreSubcommand =
          Opts.subcommand("core", help = "Execute using CORE.") {
            val demoOps = {
              val demo = Opts.flag("demo", help = "Run the demo")
              demo.map { _ =>
                Init.startCore(common.data.Master, Port.SeedPort)
                Init.startCore(common.data.Slave, Port.RandomPort)
                Init.startCore(common.data.Slave, Port.RandomPort)
              }
            }
            val runOpts =
              Init.getRunOpts { (role, port, queryPath, strategy) =>
                Init.startCore(role, port, queryPath, strategy = strategy)
              }
            demoOps <+> runOpts
          }

        // Uses CORE2 as a dependency.
        val core2Subcommand =
          Opts.subcommand("core2", help = "Execute using CORE2.") {
            Init.getRunOpts { (role, port, queryPath, _) =>
              Init.startCore2(role, port, queryPath)
            }
          }

        coreSubcommand <+> core2Subcommand
      }
    )

object Init {
  // Creates the runCoreX CLI parsing options
  def getRunOpts(
      start: (
          Role,
          Port,
          Option[QueryPath],
          Option[DistributionStrategy]
      ) => Unit
  ): Opts[Unit] = {
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
              case Master => Port.SeedPort
              case Slave  => Port.RandomPort
            }
          case Some(port) => port
        }
        start(role, port, queryPath, strategy)
    }
  }

  // Starts CORE system.
  def startCore(
      role: Role,
      port: Port,
      queryPath: Option[QueryPath] = None,
      callback: Option[Callback] = None,
      strategy: Option[DistributionStrategy] = None,
      predicate: Option[Predicate] = None
  ): Unit = {
    val config =
      parseConfig(role, port, queryPath, strategy, predicate)

    val getWorker: QueryPath => (String, Behavior[_]) =
      _ => ("Worker", dcer.core.actors.Worker())

    val getManager: (QueryPath, FiniteDuration) => (
        String,
        Behavior[_]
    ) =
      (queryPath, warmUpTime) =>
        ("Manager", dcer.core.actors.Manager(queryPath, callback, warmUpTime))

    val _ = ActorSystem(
      Root(getWorker, getManager),
      "ClusterSystem",
      config
    )
  }

  // Starts CORE2 system.
  def startCore2(
      role: Role,
      port: Port,
      queryPath: Option[QueryPath] = None
  ): Unit = {
    val config =
      parseConfig(role, port, queryPath, None, None)

    val getWorker: QueryPath => (String, Behavior[_]) =
      queryPath => ("Worker", dcer.core2.actors.Worker(queryPath))

    val getManager: (QueryPath, FiniteDuration) => (
        String,
        Behavior[_]
    ) =
      (_, warmUpTime) => ("Manager", dcer.core2.actors.Manager(warmUpTime))

    val _ = ActorSystem(
      Root(getWorker, getManager),
      "ClusterSystem",
      config
    )
  }

  private def parseConfig(
      role: Role,
      port: Port,
      queryPath: Option[QueryPath],
      strategy: Option[DistributionStrategy],
      predicate: Option[Predicate]
  ): Config = {
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
  }
}
