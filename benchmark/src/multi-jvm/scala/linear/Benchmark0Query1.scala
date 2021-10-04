package linear

import dcer.StartUp
import dcer.data
import dcer.data.{Port, QueryPath}

object Benchmark0Query1MultiJvmNode1 {
  def main(args: Array[String]): Unit = {
    val query = QueryPath("./benchmark/benchmark_0/query1/").get
    StartUp.startup(data.Engine, Port.SeedPort, Some(query))
  }
}

object Benchmark0Query1MultiJvmNode2 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}

object Benchmark0Query1MultiJvmNode3 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}

object Benchmark0Query1MultiJvmNode4 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}
