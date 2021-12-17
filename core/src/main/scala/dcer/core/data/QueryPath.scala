package dcer.core.data

import java.nio.file.{Files, Path, Paths}

sealed abstract case class QueryPath(value: Path)

object QueryPath {
  def apply(filePath: String): Option[QueryPath] = {
    apply(Paths.get(filePath))
  }

  def apply(filePath: Path): Option[QueryPath] = {
    if (Files.exists(filePath)) {
      Some(new QueryPath(filePath) {})
    } else {
      None
    }
  }
}
