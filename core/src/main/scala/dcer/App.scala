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
  Slave,
  Strategy,
  StrategyObject
}
import dcer.core.data.{DistributionStrategy => DS1}
import dcer.core2.data.{DistributionStrategy => DS2}

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
            val runOpts =
              Init.getRunOpts(DS1) { (role, port, queryPath, strategy) =>
                Init.startCore(role, port, queryPath, strategy = strategy)
              }

            val demoOps = {
              val demo = Opts.flag("demo", help = "Run the demo")
              demo.map { _ =>
                Init.startCore(
                  common.data.Master,
                  Port.SeedPort,
                  strategy = Some(DS1.RoundRobin)
                )
                Init.startCore(common.data.Slave, Port.RandomPort)
                Init.startCore(common.data.Slave, Port.RandomPort)
              }
            }

            demoOps <+> runOpts
          }

        // Uses CORE2 as a dependency.
        val core2Subcommand =
          Opts.subcommand("core2", help = "Execute using CORE2.") {
            val runOpts =
              Init.getRunOpts(DS2) { (role, port, queryPath, strategy) =>
                Init.startCore2(role, port, queryPath, strategy)
              }

            // There are n engines simultaneously making it impossible to run them concurrently since
            // CORE uses static global variables aplenty.
//            val demoOps = {
//              val demo = Opts.flag("demo", help = "Run the demo")
//              demo.map { _ =>
//                Init.startCore2(DS2)(
//                  common.data.Master,
//                  Port.SeedPort,
//                  strategy = Some(DS2.Distributed)
//                )
//                Init.startCore2(DS2)(common.data.Slave, Port.RandomPort)
//                Init.startCore2(DS2)(common.data.Slave, Port.RandomPort)
//              }
//            }

            runOpts
          }

        coreSubcommand <+> core2Subcommand
      }
    )

object Init {
  // Creates the runCoreX CLI parsing options
  def getRunOpts(s: StrategyObject)(
      start: (
          Role,
          Port,
          Option[QueryPath],
          Option[s.R]
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
          help = s"Available strategies: ${s.all}"
        )
        .mapValidated { str =>
          s.parse(str).toValidNel(s"Invalid strategy: $str")
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
      strategy: Option[Strategy] = None,
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
      queryPath: Option[QueryPath] = None,
      strategy: Option[Strategy] = None
  ): Unit = {
    val config =
      parseConfig(role, port, queryPath, strategy, None)

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
      strategy: Option[Strategy],
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
      show = (_: Strategy).toString
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
