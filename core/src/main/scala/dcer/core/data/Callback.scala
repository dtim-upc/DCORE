package dcer.core.data

import dcer.core.actors.EngineManager.MatchGroupingId

/** Callback
  * @param matchFound
  *   Call when a '''validated''' match is found.
  * @param exit
  *   Call when all workers have finished.
  */
case class Callback(
    matchFound: (MatchGroupingId, Match) => Unit,
    exit: () => Unit
)
