package dcer

import scala.util.Try

object Implicits extends AllSyntax

trait AllSyntax extends StringSyntax

trait StringSyntax {
  implicit final def toStringOps(str: String): StringOps =
    new StringOps(str)
}

final class StringOps(private val str: String) extends AnyVal {
  def parseInt: Option[Int] = Try(str.toInt).toOption
  def parseLong: Option[Long] = Try(str.toLong).toOption
  def parseDouble: Option[Double] = Try(str.toDouble).toOption
}
