package dcer.core.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.typed.Cluster
import dcer.core.data.{
  ActorAddress,
  Address,
  Configuration,
  QueryPath,
  Callback
}
import dcer.core.{actors, data}

import scala.concurrent.duration.DurationInt

object Root {
  sealed trait Command
  final case class ActorTerminated(name: String) extends Command

  def apply(
      callback: Option[Callback]
  ): Behavior[Root.ActorTerminated] =
    Behaviors.setup { ctx =>
      val cluster = Cluster(ctx.system)
      val config = Configuration(ctx)
      val address = Address.fromCtx(ctx)

      cluster.selfMember.roles match {
        case roles if roles.contains(data.Worker.toString) =>
          val workersPerNode =
            config.getInt(Configuration.WorkersPerNodeKey)

          (1 to workersPerNode).foreach { n =>
            val actorName = "Worker"
            val actorAddress = ActorAddress(actorName, id = Some(n), address)
            val actor = ctx.spawn(actors.Worker(), actorAddress.toString)
            ctx.watchWith(actor, ActorTerminated(actorName))
          }

          running(ctx, workersPerNode)

        // Recall it is possible to create a cluster singleton actor.
        case roles if roles.contains(data.Engine.toString) =>
          val queryPath =
            config.getValueOrThrow(Configuration.QueryPathKey)(QueryPath.apply)

          val warmUpTime =
            config.getInt(Configuration.WarmUpTimeKey).seconds

          val actorName = "EngineManager"
          val actorAddress = ActorAddress(actorName, id = None, address)
          val actor = ctx.spawn(
            actors.EngineManager(
              queryPath,
              callback,
              warmUpTime
            ),
            actorAddress.toString
          )
          ctx.watchWith(actor, ActorTerminated(actorAddress.toString))
          running(ctx, activeActors = 1)

        case _ =>
          ctx.log.error("Role not properly set.")
          Behaviors.stopped
      }
    }

  private def running(
      ctx: ActorContext[Root.ActorTerminated],
      activeActors: Int
  ): Behavior[Root.ActorTerminated] =
    Behaviors.receiveMessage { case Root.ActorTerminated(name) =>
      ctx.log.info(s"Actor $name terminated.")
      val remainingActors = activeActors - 1
      remainingActors match {
        case 0 =>
          ctx.log.info("All actors terminated")
          Behaviors.stopped
        case n =>
          running(ctx, activeActors = n)
      }
    }
}