package dcer.common.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.typed.Cluster
import dcer.common.data.{
  ActorAddress,
  Address,
  Configuration,
  Master,
  QueryPath,
  Slave
}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Root {
  sealed trait Command
  final case class ActorTerminated(name: String) extends Command

  def apply(
      getWorker: (QueryPath) => (String, Behavior[_]),
      getManager: (QueryPath, FiniteDuration) => (
          String,
          Behavior[_]
      )
  ): Behavior[Root.ActorTerminated] =
    Behaviors.setup { ctx =>
      val cluster = Cluster(ctx.system)
      val config = Configuration(ctx)
      val address = Address.fromCtx(ctx)
      val queryPath =
        config.getValueOrThrow(Configuration.QueryPathKey)(QueryPath.apply)

      cluster.selfMember.roles match {
        case roles if roles.contains(Slave.toString) =>
          val workersPerNode =
            config.getInt(Configuration.WorkersPerNodeKey)

          (1 to workersPerNode).foreach { n =>
            val (actorName, worker) = getWorker(queryPath)
            val actorAddress = ActorAddress(actorName, id = Some(n), address)
            val actor = ctx.spawn(worker, actorAddress.toString)
            ctx.watchWith(actor, ActorTerminated(actorName))
          }

          running(ctx, workersPerNode)

        // Recall it is possible to create a cluster singleton actor.
        case roles if roles.contains(Master.toString) =>
          val warmUpTime =
            config.getInt(Configuration.WarmUpTimeKey).seconds

          val (actorName, manager) = getManager(queryPath, warmUpTime)
          val actorAddress = ActorAddress(actorName, id = None, address)
          val actor = ctx.spawn(manager, actorAddress.toString)
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
