package dcer

import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.engine.{BaseEngine, Engine}
import edu.puc.core.runtime.events.{Event => CoreEvent}
import edu.puc.core.util.StringUtils

import scala.util.Random

object Common {
  def getNewEngine(): (Engine, EventProducer) = {
    BaseEngine.clear()
    // NOTE: Paths.get(".").toAbsolutePath from core/main returns "./core/"
    // but from core/test returns "."
    val queryPath: String = "./core/src/test/resources/query_1"
    val queryFile = StringUtils.getReader(queryPath + "/query_test.data")
    val streamFile = StringUtils.getReader(queryPath + "/stream_test.data")
    val executorManager = ExecutorManager.fromCOREFile(queryFile)
    val streamManager = StreamManager.fromCOREFile(streamFile)
    val engine =
      BaseEngine.newEngine(executorManager, streamManager, false, true, true)
    engine.start()
    (engine, new EventProducer {})
  }

  sealed trait EventProducer {
    def getEventAtRandom(): CoreEvent = {
      val rng = new Random()
      rng.nextBoolean() match {
        case false =>
          val temp: java.lang.Double = rng.nextDouble()
          val city: java.lang.String = rng.nextString(5)
          new CoreEvent("S", "T", temp, city)
        case true =>
          val hum: java.lang.Double = rng.nextDouble()
          val city: java.lang.String = rng.nextString(5)
          new CoreEvent("S", "H", hum, city)
      }
    }
  }
}
