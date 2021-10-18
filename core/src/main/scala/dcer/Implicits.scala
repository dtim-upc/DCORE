package dcer

import scala.util.Try

object Implicits extends AllSyntax

trait AllSyntax extends StringSyntax with ListListSyntax

trait StringSyntax {
  implicit final def toStringOps(str: String): StringOps =
    new StringOps(str)
}

final class StringOps(private val str: String) extends AnyVal {
  def parseInt: Option[Int] = Try(str.toInt).toOption
  def parseLong: Option[Long] = Try(str.toLong).toOption
  def parseDouble: Option[Double] = Try(str.toDouble).toOption
}

trait ListListSyntax {
  // TODO @specialized(Int, Double, Long) A
  implicit final def toListListOps[A](
      xss: List[List[A]]
  ): ListListOps[A] =
    new ListListOps[A](xss)
}

final class ListListOps[A](
    private val xss: List[List[A]]
) extends AnyVal {
  def cartesianProduct: List[List[A]] = {
    @inline def partialCartesian(
        a: List[List[A]],
        b: List[A]
    ): List[List[A]] = {
      a.flatMap(xs => {
        b.map(y => {
          xs ++ List(y)
        })
      })
    }

    xss.headOption match {
      case Some(head) => {
        val tail = xss.tail
        val init = head.map(n => List(n))
        tail.foldLeft(init)((arr, list) => {
          partialCartesian(arr, list)
        })
      }
      case None => {
        List()
      }
    }
  }
}
