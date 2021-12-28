package generator

import better.files.Dsl._
import better.files._
import com.monovore.decline._
import dcer.common.data.{Predicate, Strategy}

// TODO
// This could have been implemented using metaprogramming (macros)
// and type checked by the compiler.

object App
    extends CommandApp(
      name = "bench-gen",
      header = "Benchmark generator for Distributed CER.",
      main = {
        Opts {
          val allBenchmarks = List(Benchmark0, Benchmark1, Benchmark2)

          allBenchmarks.foreach { benchmark =>
            Generator.generate(benchmark)
          }
        }
      }
    )

object Generator {
  val ProjectRoot: File = pwd / "benchmark"

  def generate(benchmark: Benchmark): Unit = {
    val BenchmarkDir = ProjectRoot / benchmark.path
    val ExecutableDir = ProjectRoot / "src" / "multi-jvm" / "scala"

    if (BenchmarkDir.exists) {
      throw new RuntimeException(
        s"${BenchmarkDir.path.toString} already exists! Delete before generating a new one."
      )
    } else {
      BenchmarkDir.createDirectory()
    }

    val generateQueryN = benchmark.generateQuery(rootDir = BenchmarkDir)(_, _)
    val generateCodeN =
      generateCode(rootDir = ExecutableDir)(_, _, _, _, _, _, _)

    (1 to benchmark.iterations) foreach { iteration =>
      Project.all.foreach { project =>
        val queryDir = generateQueryN(iteration, project)
        benchmark.jvmWorkers.foreach { nWorkers =>
          Predicate.all foreach { predicate =>
            project.strategies.foreach { strategy =>
              generateCodeN(
                benchmark,
                iteration,
                queryDir,
                strategy,
                predicate,
                nWorkers,
                project
              )
            }
          }
        }
      }
    }
  }

  private def generateCode(rootDir: File)(
      benchmark: Benchmark,
      query: Int,
      queryDir: File,
      strategy: Strategy,
      predicate: Predicate,
      nWorkers: Int,
      project: Project
  ): Unit = {
    val predicatePath = predicate.toString.toLowerCase()
    val queryPath = s"query${query}"
    val nWorkersPath = s"workers${nWorkers}"

    val packageDir =
      (rootDir / project.path / benchmark.path / queryPath / nWorkersPath / predicatePath)
        .createDirectoryIfNotExists()

    def className(jvm: Int): String =
      s"${strategy}MultiJvmNode${jvm}"

    val packageName =
      s"${project.path}.${benchmark.path}.${queryPath}.${nWorkersPath}.${predicatePath}"
    val packageDec =
      s"""package ${packageName}
        |""".stripMargin

    val importsDec =
      s"""import dcer.Init._
        |import dcer.common.data
        |import dcer.common.data.{Port, QueryPath}
        |import dcer.common.data.Predicate._
        |import dcer.${project.path}.data.DistributionStrategy._
        |""".stripMargin

    val engineActorDec =
      s"""object ${className(jvm = 1)} {
         |  def main(args: Array[String]): Unit = {
         |    val query = QueryPath("${queryDir}").get
         |    ${project match {
        case Core =>
          s"startCore(data.Master, Port.SeedPort, Some(query), strategy = Some(${strategy}), predicate = Some(${predicate}()))"
        case Core2 =>
          s"startCore2(data.Master, Port.SeedPort, Some(query), strategy = Some(${strategy}), predicate = Some(${predicate}()))"
      }}
         |  }
         |}
         |""".stripMargin

    def workerActorDec(n: Int): String =
      s"""object ${className(jvm = n)} {
         |  def main(args: Array[String]): Unit = {
         |    val query = QueryPath("${queryDir}").get
         |    ${project match {
        case Core =>
          s"startCore(data.Slave, Port.RandomPort, Some(query))"
        case Core2 =>
          s"startCore2(data.Slave, Port.RandomPort, Some(query))"
      }}
         |  }
         |}
         |""".stripMargin

    val sourceFile =
      (packageDir / s"${strategy}.scala")
        .createFileIfNotExists()

    sourceFile << packageDec
    sourceFile << importsDec
    sourceFile << engineActorDec
    (1 to nWorkers) foreach { worker =>
      sourceFile << workerActorDec(n = worker + 1 /*actor 1 is the Engine*/ )
    }
  }
}

// Hey you! Do not forget to add the benchmark to `App`
trait Benchmark {
  // Unique identifier
  val id: Int

  // Relative benchmark path
  def path: String = s"benchmark${id}"

  // Number of iterations this benchmark must be run.
  // Usually, the complexity of the test scales with the iteration number.
  val iterations: Int

  // The benchmark must be executed for each JVM Workers size.
  // NB: each JVM spawns n workers (1 by default).
  val jvmWorkers: List[Int] = List(1, 2, 4, 6, 8, 10, 12)

  // Given the rootDir and the iteration number generates a query file.
  def generateQuery(rootDir: File)(iteration: Int, project: Project): File
}

object Benchmark0 extends Benchmark {
  override val id: Int = 0
  override val iterations: Int = 1

  override def generateQuery(
      rootDir: File
  )(iteration: Int, project: Project): File = {
    val queryDir =
      (rootDir / project.path / s"query$iteration").createDirectories()

    val querySubDir = (queryDir / "query").createDirectory()
    val queryFile = (querySubDir / "queries").createFile()
    val descriptionFile = (querySubDir / "StreamDescription.txt").createFile()

    val streamDir = (queryDir / "stream").createDirectory()
    val streamFile = (streamDir / "stream").createFile()

    (queryDir / "query_test.data")
      .createFile()
      .writeText(s"""|FILE:${descriptionFile}
                     |FILE:${queryFile}
                     |""".stripMargin)

    (queryDir / "stream_test.data")
      .createFile()
      .writeText(s"S:FILE:${streamFile}")

    descriptionFile
      .writeText("""DECLARE EVENT A(id int)
                   |DECLARE EVENT B(id int)
                   |DECLARE EVENT C(id int)
                   |DECLARE STREAM S(A, B, C)
                   |""".stripMargin)

    queryFile
      .writeText(s"""SELECT *
                    |FROM S
                    |WHERE (A as a1; B as b1 ; C as c1)
                    |${project match {
        case Core  => ""
        case Core2 => "WITHIN 100000000 EVENTS"
      }}
                    |""".stripMargin)

    // Stream file with events
    {
      val n = iteration match {
        case 1  => 1500
        case it => throw new RuntimeException(s"Iteration $it not implemented")
      }
      (1 to n).foreach { i =>
        streamFile << s"A(id=$i)"
      }
      (1 to n).foreach { i =>
        streamFile << s"B(id=$i)"
      }
      streamFile << "C(id=1)"
    }

    queryDir
  }
}

object Benchmark1 extends Benchmark {
  override val id: Int = 1
  override val iterations: Int = 1

  override def generateQuery(
      rootDir: File
  )(iteration: Int, project: Project): File = {
    val queryDir =
      (rootDir / project.path / s"query$iteration").createDirectories()

    val querySubDir = (queryDir / "query").createDirectory()
    val queryFile = (querySubDir / "queries").createFile()
    val descriptionFile = (querySubDir / "StreamDescription.txt").createFile()

    val streamDir = (queryDir / "stream").createDirectory()
    val streamFile = (streamDir / "stream").createFile()

    (queryDir / "query_test.data")
      .createFile()
      .writeText(s"""|FILE:${descriptionFile}
                     |FILE:${queryFile}
                     |""".stripMargin)

    (queryDir / "stream_test.data")
      .createFile()
      .writeText(s"S:FILE:${streamFile}")

    descriptionFile
      .writeText("""DECLARE EVENT T(temp double, city string)
                   |DECLARE EVENT H(hum double, city string)
                   |DECLARE STREAM S(T, H)
                   |""".stripMargin)

    queryFile
      .writeText(s"""SELECT *
                    |FROM S
                    |WHERE (T as t1 ; H + as hs ; H as h1)
                    |FILTER
                    |    (t1[temp < 0] AND
                    |     hs[hum < 60] AND
                    |     h1[hum > 60])
                    |${project match {
        case Core  => ""
        case Core2 => "WITHIN 100000000 EVENTS"
      }}
                    |""".stripMargin)

    // Stream file with events
    {
      streamFile << "T(temp=-2, city=barcelona)"
      val n = iteration match {
        case 1  => 30
        case it => throw new RuntimeException(s"Iteration $it not implemented")
      }
      val r = scala.util.Random
      (1 to n).foreach { _ =>
        val hum = r.nextInt(60) // 0 to 59
        streamFile << s"H(hum=$hum, city=barcelona)"
      }
      streamFile << "H(hum=65, city=barcelona)"
    }

    queryDir
  }
}

object Benchmark2 extends Benchmark {
  override val id: Int = 2
  override val iterations: Int = 1

  override def generateQuery(
      rootDir: File
  )(iteration: Int, project: Project): File = {
    val queryDir =
      (rootDir / project.path / s"query$iteration").createDirectories()

    val querySubDir = (queryDir / "query").createDirectory()
    val queryFile = (querySubDir / "queries").createFile()
    val descriptionFile = (querySubDir / "StreamDescription.txt").createFile()

    val streamDir = (queryDir / "stream").createDirectory()
    val streamFile = (streamDir / "stream").createFile()

    (queryDir / "query_test.data")
      .createFile()
      .writeText(s"""|FILE:${descriptionFile}
                     |FILE:${queryFile}
                     |""".stripMargin)

    (queryDir / "stream_test.data")
      .createFile()
      .writeText(s"S:FILE:${streamFile}")

    descriptionFile
      .writeText("""DECLARE EVENT A(id int)
                   |DECLARE EVENT B(id int)
                   |DECLARE EVENT C(id int)
                   |DECLARE STREAM S(A, B, C)
                   |""".stripMargin)

    queryFile
      .writeText(s"""SELECT *
                   |FROM S
                   |WHERE (A + as aa ; B + as bb ; C as c1)
                   |${project match {
        case Core  => ""
        case Core2 => "WITHIN 100000000 EVENTS"
      }}
                   |""".stripMargin)

    // Stream file with events
    {
      val n = iteration match {
        case 1  => 17
        case it => throw new RuntimeException(s"Iteration $it not implemented")
      }
      (1 to n).foreach { i =>
        streamFile << s"A(id=$i)"
        streamFile << s"B(id=$i)"
      }
      streamFile << "C(id=1)"
    }

    queryDir
  }
}

sealed trait Project {
  val path: String
  val strategies: List[Strategy]
}
object Project {
  val all: List[Project] = List(Core, Core2)
}
case object Core extends Project {
  override val path = "core"
  override val strategies: List[Strategy] =
    dcer.core.data.DistributionStrategy.all
}
case object Core2 extends Project {
  override val path = "core2"
  override val strategies: List[Strategy] =
    dcer.core2.data.DistributionStrategy.all
}
