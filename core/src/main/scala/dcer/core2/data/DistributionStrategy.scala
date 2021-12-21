package dcer.core2.data

import dcer.common.data.{Strategy, StrategyObject}

sealed trait DistributionStrategy extends Strategy

object DistributionStrategy extends StrategyObject {

  case object Sequential extends DistributionStrategy
  case object Distributed extends DistributionStrategy

  type R = DistributionStrategy

  override def parse(s: String): Option[DistributionStrategy] = {
    s.toLowerCase match {
      case x if x == Sequential.toString.toLowerCase =>
        Some(Sequential)
      case x if x == Distributed.toString.toLowerCase =>
        Some(Distributed)
      case _ =>
        None
    }
  }

  override val all: List[DistributionStrategy] =
    List(
      Sequential,
      Distributed
    )
}
