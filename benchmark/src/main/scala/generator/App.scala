package generator

import better.files.Dsl._
import better.files._
import com.monovore.decline._
import dcer.distribution.Predicate

// TODO
// This could have been implemented using metaprogramming (macros)
// and type checked by the compiler.

object App
    extends CommandApp(
      name = "bench-gen",
      header = "Benchmark generator for Distributed CER.",
      main = {
        Opts {
          (0 until 1) foreach { benchmark =>
            Generator.generate(benchmark)
          }
        }
      }
    )

object Generator {
  val projectRoot: File = pwd / "benchmark"
  // Each query has increasing complexity (plus one event in the kleene plus).
  val nQueries: Int = 10

  def generate(benchmark: Int): Unit = {
    val benchmarkDir = projectRoot / s"benchmark_${benchmark}"
    val codeDir = projectRoot / "src" / "multi-jvm" / "scala"

    if (benchmarkDir.exists) {
      throw new RuntimeException(
        s"${benchmarkDir.path.toString} already exists! Delete before generating a new one."
      )
    } else {
      benchmarkDir.createDirectory()
    }

    val generateQueryN = generateQuery(rootDir = benchmarkDir)(_)
    val generateCodeN = generateCode(rootDir = codeDir)(_, _, _, _)

    (1 to nQueries) foreach { query =>
      val queryDir = generateQueryN(query)
      Predicate.all foreach { predicate =>
        if (predicate == Predicate.Linear()) {
          generateCodeN(benchmark, query, predicate, queryDir)
        } else {
          println(s"Predicate $predicate not implemented yet")
        }
      }
    }
  }

  private def generateQuery(rootDir: File)(query: Int): File = {
    val queryDir = (rootDir / s"query_$query").createDirectory()

    val querySubDir = (queryDir / "query").createDirectory()
    val queryFile = (querySubDir / "queries").createFile()
    val descriptionFile = (querySubDir / "StreamDescription.txt").createFile()

    val streamDir = (queryDir / "stream").createDirectory()
    val streamFile = (streamDir / "stream").createFile()

    val queryTestFile =
      (queryDir / "query_test.data")
        .createFile()
        .writeText(s"""|FILE:${descriptionFile}
                       |FILE:${queryFile}
                       |""".stripMargin)

    val streamTestFile =
      (queryDir / "stream_test.data")
        .createFile()
        .writeText(s"S:FILE:${streamFile}")

    descriptionFile
      .writeText("""DECLARE EVENT T(temp double, city string)
                   |DECLARE EVENT H(hum double, city string)
                   |DECLARE STREAM S(T, H)
                   |""".stripMargin)

    queryFile
      .writeText("""SELECT *
                   |FROM S
                   |WHERE (T as t1 ; H + as hs ; H as h1)
                   |FILTER
                   |    (t1[temp < 0] AND
                   |     hs[hum < 60] AND
                   |     h1[hum > 60])
                   |""".stripMargin)

    // Stream file with events
    {
      streamFile << "T(temp=-2, city=barcelona)"
      val r = scala.util.Random
      (1 to query).foreach { _ =>
        val hum = r.nextInt(60) // 0 to 59
        streamFile << s"H(hum=$hum, city=barcelona)"
      }
      streamFile << "H(hum=65, city=barcelona)"
    }

    queryDir
  }

  private def generateCode(rootDir: File)(
      benchmark: Int,
      query: Int,
      predicate: Predicate,
      queryDir: File
  ): Unit = {
    val packageName = predicate match {
      case Predicate.Linear()    => "linear"
      case Predicate.Quadratic() => "quadratic"
      case Predicate.Cubic()     => "cubic"
    }

    val packageDir = (rootDir / packageName).createDirectoryIfNotExists()

    def className(jvm: Int): String =
      s"Benchmark${benchmark}Query${query}MultiJvmNode${jvm}"

    val packageDec =
      s"""package ${packageName}
        |""".stripMargin

    val importsDec =
      """import dcer.StartUp
        |import dcer.data
        |import dcer.data.{Port, QueryPath}
        |""".stripMargin

    val engineActorDec =
      s"""object ${className(jvm = 1)} {
         |  def main(args: Array[String]): Unit = {
         |    val query = QueryPath("${queryDir}").get
         |    StartUp.startup(data.Engine, Port.SeedPort, Some(query))
         |  }
         |}
         |""".stripMargin

    def workerActorDec(n: Int): String =
      s"""object ${className(jvm = n)} {
         |  def main(args: Array[String]): Unit = {
         |    StartUp.startup(data.Worker, Port.RandomPort)
         |  }
         |}
         |""".stripMargin

    val sourceFile =
      (packageDir / s"Benchmark${benchmark}Query${query}.scala")
        .createFileIfNotExists()

    sourceFile << packageDec
    sourceFile << importsDec
    sourceFile << engineActorDec
    (2 to 4) foreach { worker =>
      sourceFile << workerActorDec(worker)
    }
  }
}