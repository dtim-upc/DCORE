package generator

import better.files.Dsl._
import com.monovore.decline._

object Benchmark0
    extends CommandApp(
      name = "benchmark",
      header = "Benchmark generator for Distributed CER.",
      main = {
        Opts {
          Generator0.generate()
        }
      }
    )

object Generator0 {
  def generate(): Unit = {
    val projectDir = pwd / "benchmark"
    val benchmarkDir = projectDir / "benchmark_0"

    if (benchmarkDir.exists) {
      println(
        s"${benchmarkDir.path.toString} already exists! Delete before generating a new one."
      )
      System.exit(-1)
    } else {
      benchmarkDir.createDirectory()
    }

    (1 to 10).foreach { i =>
      val queryNDir = (benchmarkDir / s"query$i").createDirectory()

      val queryDir = (queryNDir / "query").createDirectory()
      val queryFile = (queryDir / "queries").createFile()
      val descriptionFile = (queryDir / "StreamDescription.txt").createFile()

      val streamDir = (queryNDir / "stream").createDirectory()
      val streamFile = (streamDir / "stream").createFile()

      val queryTestFile =
        (queryNDir / "query_test.data")
          .createFile()
          .writeText(s"""|FILE:${descriptionFile}
                         |FILE:${queryFile}
                         |""".stripMargin)

      val streamTestFile =
        (queryNDir / "stream_test.data")
          .createFile()
          .writeText(s"S:FILE:${streamFile}")

      descriptionFile.writeText("""DECLARE EVENT T(temp double, city string)
                                  |DECLARE EVENT H(hum double, city string)
                                  |DECLARE STREAM S(T, H)
                                  |""".stripMargin)

      queryFile.writeText("""SELECT *
                            |FROM S
                            |WHERE (T as t1 ; H + as hs ; H as h1)
                            |FILTER
                            |    (t1[temp < 0] AND
                            |     hs[hum < 60] AND
                            |     h1[hum > 60])
                            |""".stripMargin)

      streamFile << "T(temp=-2, city=barcelona)"
      val r = scala.util.Random
      (1 to i * 2).foreach { _ =>
        val hum = r.nextInt(60) // 0 to 59
        streamFile << s"H(hum=$hum, city=barcelona)"
      }
      streamFile << "H(hum=65, city=barcelona)"
    }
  }
}
