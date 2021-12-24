package dcer.core2.distribution

import akka.actor.typed.scaladsl.ActorContext
import dcer.common.data.{Configuration, Predicate}
import dcer.core2.actors.{Manager, Worker}
import dcer.core2.data.DistributionStrategy
import edu.puc.core2.util.DistributionConfiguration

sealed trait Distributor {

  val ctx: ActorContext[Manager.Event]
  val workers: Array[Worker.Ref]
  val predicate: Predicate

  def distributeWorkload(): Unit
}

object Distributor {
  def apply(
      distributionStrategy: DistributionStrategy,
      ctx: ActorContext[Manager.Event],
      workers: Array[Worker.Ref],
      predicate: Predicate
  ): Distributor = {
    distributionStrategy match {
      case DistributionStrategy.Sequential =>
        Sequential(ctx, workers, predicate)
      case DistributionStrategy.Distributed =>
        Distributed(ctx, workers, predicate)
    }
  }

  def fromConfig(config: Configuration.Parser)(
      ctx: ActorContext[Manager.Event],
      workers: Array[Worker.Ref]
  ): Distributor = {

    val strategy: DistributionStrategy =
      config.getValueOrThrow(Configuration.DistributionStrategyKey)(
        DistributionStrategy.parse
      )

    val predicate: Predicate =
      config.getValueOrThrow(Configuration.SecondOrderPredicateKey)(
        Predicate.parse
      )

    Distributor(strategy, ctx, workers, predicate)
  }

  private case class Sequential(
      ctx: ActorContext[Manager.Event],
      workers: Array[Worker.Ref],
      predicate: Predicate
  ) extends Distributor {
    val (theWorker, restOfWorkers) = workers match {
      case Array(hd, tl @ _*) => (hd, tl)
      case _ => throw new RuntimeException("Expecting at least one worker.")
    }

    override def distributeWorkload(): Unit = {
      val config = DistributionConfiguration.DEFAULT
      ctx.log.info(s"Sending start to worker ${theWorker.path}")
      theWorker ! Worker.Start(
        config.process,
        config.processes,
        ctx.self,
        predicate
      )
      restOfWorkers.foreach { worker =>
        ctx.log.info(s"Sending stop to worker ${worker.path}")
        worker ! Worker.Stop(ctx.self)
      }
    }
  }

  private case class Distributed(
      ctx: ActorContext[Manager.Event],
      workers: Array[Worker.Ref],
      predicate: Predicate
  ) extends Distributor {
    override def distributeWorkload(): Unit = {
      workers.zipWithIndex.foreach { case (worker, index) =>
        ctx.log.info(s"Sending start to worker ${worker.path}")
        worker ! Worker.Start(index, workers.length, ctx.self, predicate)
      }
    }
  }
}
