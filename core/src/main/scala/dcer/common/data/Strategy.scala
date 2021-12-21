package dcer.common.data

trait Strategy

trait StrategyObject {
  type R
  def parse(s: String): Option[R]
  val all: List[R]
}
