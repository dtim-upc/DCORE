package dcer.common.data

import dcer.core.actors.Manager.MatchGroupingId
import dcer.core.data.Match

/** Callback
  *
  * @param matchFound
  *   Call when a '''validated''' match is found.
  * @param exit
  *   Call when all workers have finished.
  */
case class Callback(
    matchFound: (MatchGroupingId, Match) => Unit,
    exit: () => Unit
)
