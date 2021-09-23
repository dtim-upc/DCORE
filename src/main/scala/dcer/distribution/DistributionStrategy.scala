package dcer.distribution

sealed trait DistributionStrategy
object DistributionStrategy {

  case object NoStrategy extends DistributionStrategy
  case object RoundRobin extends DistributionStrategy
  case object DoubleHashing extends DistributionStrategy
  case object SubMatchesFromMaximalMatch extends DistributionStrategy
  case object SubMatchesBySizeFromMaximalMatch extends DistributionStrategy
  case object NoCollisionsEnumeration extends DistributionStrategy

  // Don't forget to update this
  val all: List[DistributionStrategy] =
    List(
      NoStrategy,
      RoundRobin,
      DoubleHashing,
      SubMatchesFromMaximalMatch,
      SubMatchesBySizeFromMaximalMatch,
      NoCollisionsEnumeration
    )

  def parse(str: String): Option[DistributionStrategy] = {
    all.find(_.toString.toLowerCase == str.toLowerCase)
  }
}
