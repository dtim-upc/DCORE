import dcer.StartUp
import dcer.data
import dcer.data.{Port, QueryPath}

object SampleMultiJvmNode1 {
  def main(args: Array[String]): Unit = {
    val query = QueryPath("./src/main/resources/query_0").get
    StartUp.startup(data.Engine, Port.SeedPort, Some(query))
  }
}

object SampleMultiJvmNode2 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}

object SampleMultiJvmNode3 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}

object SampleMultiJvmNode4 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Worker, Port.RandomPort)
  }
}
