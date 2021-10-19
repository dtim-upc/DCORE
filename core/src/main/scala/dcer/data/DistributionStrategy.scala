package dcer.data

sealed trait DistributionStrategy
object DistributionStrategy {

  case object Sequential extends DistributionStrategy
  case object RoundRobin extends DistributionStrategy
  case object RoundRobinWeighted extends DistributionStrategy
  case object PowerOfTwoChoices extends DistributionStrategy
  case object MaximalMatches extends DistributionStrategy

  def parse(s: String): Option[DistributionStrategy] = {
    s.toLowerCase match {
      case x if x == Sequential.toString.toLowerCase =>
        Some(Sequential)
      case x if x == RoundRobin.toString.toLowerCase =>
        Some(RoundRobin)
      case x if x == RoundRobinWeighted.toString.toLowerCase =>
        Some(RoundRobinWeighted)
      case x if x == PowerOfTwoChoices.toString.toLowerCase =>
        Some(PowerOfTwoChoices)
      case x if x == MaximalMatches.toString.toLowerCase =>
        Some(MaximalMatches)
      case _ =>
        None
    }
  }

  // This could be done by reflection
  def all: List[DistributionStrategy] =
    List(
      Sequential,
      RoundRobin,
      RoundRobinWeighted,
      PowerOfTwoChoices,
      MaximalMatches
    )
}
