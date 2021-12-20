package dcer.core2.data

sealed trait DistributionStrategy
object DistributionStrategy {
  case object Sequential extends DistributionStrategy
  case object Distributed extends DistributionStrategy

  def parse(s: String): Option[DistributionStrategy] = {
    s.toLowerCase match {
      case x if x == Sequential.toString.toLowerCase =>
        Some(Sequential)
      case x if x == Distributed.toString.toLowerCase =>
        Some(Distributed)
      case _ =>
        None
    }
  }

  def all: List[DistributionStrategy] =
    List(
      Sequential,
      Distributed
    )
}
