package dcer.data

import java.nio.file.{Files, Paths}

sealed abstract case class QueryPath(value: String)

object QueryPath {
  def apply(filePath: String): Option[QueryPath] = {
    if (Files.exists(Paths.get(filePath))) {
      Some(new QueryPath(filePath) {})
    } else {
      None
    }
  }
}
