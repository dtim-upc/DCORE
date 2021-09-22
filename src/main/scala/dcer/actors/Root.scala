package dcer.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.typed.Cluster
import dcer.data.Configuration
import dcer.distribution.{DistributionStrategy, SecondOrderPredicate}
import dcer.{actors, data}

import scala.concurrent.duration.DurationInt

object Root {
  sealed trait Command
  final case class ActorTerminated(name: String) extends Command

  def apply(): Behavior[Root.ActorTerminated] = Behaviors.setup { ctx =>
    val cluster = Cluster(ctx.system)
    val config = Configuration(ctx)

    cluster.selfMember.roles match {
      case roles if roles.contains(data.Worker.toString) =>
        val workersPerNode =
          config.getInt(Configuration.WorkersPerNodeKey)

        (1 to workersPerNode).foreach { n =>
          val actorName = s"Worker-$n"
          val actor = ctx.spawn(actors.Worker(), actorName)
          ctx.watchWith(actor, ActorTerminated(actorName))
        }

        running(ctx, workersPerNode)

      // Recall it is possible to create a cluster singleton actor.
      case roles if roles.contains(data.Engine.toString) =>
        val queryPath =
          config.getString(Configuration.QueryPathKey)

        val warmUpTime =
          config.getInt(Configuration.WarmUpTimeKey).seconds

        val distributionStrategy: DistributionStrategy =
          config.getValueOrThrow(Configuration.DistributionStrategyKey)(
            DistributionStrategy.parse
          )

        val secondOrderPredicate: SecondOrderPredicate =
          config.getValueOrThrow(Configuration.SecondOrderPredicateKey)(
            SecondOrderPredicate.parse
          )

        val actorName = "EngineManager"
        val actor = ctx.spawn(
          actors.EngineManager(
            queryPath,
            warmUpTime,
            distributionStrategy,
            secondOrderPredicate
          ),
          actorName
        )
        ctx.watchWith(actor, ActorTerminated(actorName))

        running(ctx, activeActors = 1, isSeedNode = true)

      case _ =>
        ctx.log.error("Role not properly set.")
        Behaviors.stopped
    }
  }

  private def running(
      ctx: ActorContext[Root.ActorTerminated],
      activeActors: Int,
      isSeedNode: Boolean = false
  ): Behavior[Root.ActorTerminated] =
    Behaviors.receiveMessage { case Root.ActorTerminated(name) =>
      ctx.log.info(s"Actor $name terminated.")
      val remainingActors = activeActors - 1
      remainingActors match {
        case 0 =>
          ctx.log.info("All actors terminated")
          if (isSeedNode) {
            // FIXME
            // Seed nodes must be stopped after regular nodes.
            // But this is not a reliable way to achieve this.
            Thread.sleep(5000)
          }
          Behaviors.stopped
        case n =>
          running(ctx, activeActors = n)
      }
    }
}
