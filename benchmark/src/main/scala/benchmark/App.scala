package benchmark

// import cats.implicits._
import com.monovore.decline._

object App
    extends CommandApp(
      name = "benchmark",
      header = "Benchmark generator for Distributed CER.",
      main = {
        Opts {
          Runner.run()
        }
      }
    )

object Runner {
  def run(): Unit = {
    println("TODO")
  }
}
