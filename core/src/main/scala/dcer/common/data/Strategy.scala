package dcer.common.data

trait Strategy {
  type R
  def parse(s: String): Option[R]
  val all: List[R]
}
