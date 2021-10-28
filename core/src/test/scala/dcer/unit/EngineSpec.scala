package dcer.unit

import better.files._
import dcer.actors.Engine
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.BufferedReader

class EngineSpec extends AnyFunSpec with Matchers {
  import EngineSpec.BufferedReaderOps

  describe("Engine") {
    describe("newMaximalMatchQueryFile") {
      it("should change the query to return SELECT MAX *") {
        val queryDataFile =
          File("./core/src/test/resources/query_1/query_test.data")
        if (!queryDataFile.exists) {
          fail(s"$queryDataFile does not exist.")
        }

        val bufferedReader =
          Engine.newMaximalMatchQueryFile(queryDataFile).right.get

        bufferedReader.readAll match {
          case first :: second :: Nil =>
            val expectedFirst =
              "FILE:./core/src/test/resources/query_1/query/StreamDescription.txt"
            first shouldBe expectedFirst
            val queryFile = File(second.split(":")(1))
            if (!queryFile.exists) {
              fail(s"$queryFile does not exist")
            }
            val expectedQuery =
              """SELECT MAX *
                |FROM S
                |WHERE (T as t1 ; H + as hs ; H as h1)
                |FILTER
                |    (t1[temp < 0] AND
                |     hs[hum < 60] AND
                |     h1[hum > 60])
                |""".stripMargin
            val resultQuery = queryFile.contentAsString
            resultQuery shouldBe expectedQuery

          case _ =>
            fail("Expecting two lines of content: description and query")
        }
      }
    }
  }
}

object EngineSpec {
  implicit class BufferedReaderOps(br: BufferedReader) {
    // Closes the reader after reading all its content
    def readAll: List[String] = {
      val content =
        Stream.continually(br.readLine()).takeWhile(_ != null).toList
      br.close()
      content
    }
  }
}
