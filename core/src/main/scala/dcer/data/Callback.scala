package dcer.data

import dcer.actors.EngineManager.MatchGroupingId

//case class Callback(f: (MatchGroupingId, Match) => Unit)
//    extends ((MatchGroupingId, Match) => Unit) {
//  override def apply(v1: MatchGroupingId, v2: Match): Unit = f(v1, v2)
//}

/** Function with existentialised parameters
  */
sealed trait Callback extends ((MatchGroupingId, Match) => Unit)

object Callback {
  def apply(f: (MatchGroupingId, Match) => Unit): Callback = new Callback {
    override def apply(v1: MatchGroupingId, v2: Match): Unit = f(v1, v2)
  }
}
