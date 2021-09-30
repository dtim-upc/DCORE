import dcer.StartUp
import dcer.data
import dcer.data.Port

object SampleMultiJvmNode1 {
  def main(args: Array[String]): Unit = {
    StartUp.startup(data.Engine, Port.SeedPort)
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
