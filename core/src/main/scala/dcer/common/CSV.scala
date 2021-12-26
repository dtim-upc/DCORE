package dcer.common

/*
It would have been better to log using
 */
object CSV {
  def header2Csv(header: List[String]): String =
    header.mkString(",")

  def toCSV(
      header: Option[List[String]],
      values: List[List[Any]]
  ): String = {
    val builder = new StringBuilder()

    header.foreach { header =>
      builder ++= header2Csv(header)
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
