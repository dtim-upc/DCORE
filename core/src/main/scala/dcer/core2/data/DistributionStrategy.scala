package dcer.core2.data

import dcer.common.data.Strategy

sealed trait DistributionStrategy

object DistributionStrategy extends Strategy {

  case object Sequential extends DistributionStrategy
  case object Distributed extends DistributionStrategy

  type R = DistributionStrategy

  override def parse(s: String): Option[R] = {
    s.toLowerCase match {
      case x if x == Sequential.toString.toLowerCase =>
        Some(Sequential)
      case x if x == Distributed.toString.toLowerCase =>
        Some(Distributed)
      case _ =>
        None
    }
  }

  override val all: List[R] =
    List(
      Sequential,
      Distributed
    )
}
