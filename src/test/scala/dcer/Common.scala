package dcer

import edu.puc.core.engine.{BaseEngine, Engine}
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.util.StringUtils
import edu.puc.core.runtime.events.{Event => CoreEvent}

import scala.util.Random

object Common {
  // You can indeed have multiple engines but the class BaseEngine
  // uses a static variable for the streams description which throws
  // if you declare multiple engines with the same stream name.
  var globalEngine: Option[Engine] = None

  def getGlobalEngine(): (Engine, EventProducer) = {
    val engine = globalEngine match {
      case Some(engine) =>
        engine
      case None =>
        val queryPath: String = "./src/main/resources/query_0"
        val queryFile = StringUtils.getReader(queryPath + "/query_test.data")
        val streamFile = StringUtils.getReader(queryPath + "/stream_test.data")
        val executorManager = ExecutorManager.fromCOREFile(queryFile)
        val streamManager = StreamManager.fromCOREFile(streamFile)
        val engine = BaseEngine.newEngine(executorManager, streamManager)
        engine.start()
        globalEngine = Some(engine)
        engine
    }
    (engine, new EventProducer)
  }

  class EventProducer {
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
