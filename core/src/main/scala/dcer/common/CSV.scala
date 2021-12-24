package dcer.common

object CSV {
  def toCSV(
      header: Option[List[String]],
      values: List[List[Any]]
  ): String = {
    val builder = new StringBuilder()

    header.foreach { header =>
      builder ++= header.mkString(",")
      builder += '\n'
    }

    values match {
      case Nil => ()
      case hd :: tl =>
        builder ++= hd.mkString(",")
        tl.foreach { line =>
          builder ++= "\n"
          builder ++= line.mkString(",")
        }
    }

    builder.result()
  }
}
