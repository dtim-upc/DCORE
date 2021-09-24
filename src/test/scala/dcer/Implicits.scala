package dcer

import edu.puc.core.engine.{BaseEngine, Engine}
import edu.puc.core.engine.executors.ExecutorManager
import edu.puc.core.engine.streams.StreamManager
import edu.puc.core.util.StringUtils
import edu.puc.core.runtime.events.{Event => CoreEvent}

import scala.util.Random

object Implicits {
  /*
  DECLARE EVENT T(temp double)
  DECLARE EVENT H(hum double)
  DECLARE STREAM S(T, H)
   */
  def getEngine(): (Engine, EventProducer) = {
    val queryPath: String = "./src/main/resources/query_0"
    val queryFile = StringUtils.getReader(queryPath + "/query_test.data")
    val streamFile = StringUtils.getReader(queryPath + "/stream_test.data")
    val executorManager = ExecutorManager.fromCOREFile(queryFile)
    val streamManager = StreamManager.fromCOREFile(streamFile)
    val engine = BaseEngine.newEngine(executorManager, streamManager)
    engine.start()
    (engine, new EventProducer)
  }

  class EventProducer {
    def getEventAtRandom(): CoreEvent = {
      val rng = new Random()
      rng.nextBoolean() match {
        case false =>
          val temp: Double = rng.nextDouble()
          new CoreEvent("S", "T", temp.asInstanceOf[AnyRef])
        case true =>
          val hum: Double = rng.nextDouble()
          new CoreEvent("S", "H", hum.asInstanceOf[AnyRef])
      }
    }
  }
}
