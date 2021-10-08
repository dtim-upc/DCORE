package dcer.data

sealed trait DistributionStrategy
object DistributionStrategy {
  case object Sequential extends DistributionStrategy
  case object RoundRobin extends DistributionStrategy

  def parse(s: String): Option[DistributionStrategy] = {
    s.toLowerCase match {
      case x if x == Sequential.toString.toLowerCase =>
        Some(Sequential)
      case x if x == RoundRobin.toString.toLowerCase =>
        Some(RoundRobin)
      case _ =>
        None
    }
  }

  def all: List[DistributionStrategy] =
    List(Sequential, RoundRobin)
}
