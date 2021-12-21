package dcer.common

import scala.util.Try

object Implicits extends AllSyntax

trait AllSyntax extends StringSyntax with ListListSyntax with MapSyntax

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
  implicit final def toListListOps[A](
      xss: List[List[A]]
  ): ListListOps[A] =
    new ListListOps[A](xss)
}

final class ListListOps[A](
    private val xss: List[List[A]]
) extends AnyVal {
  // The complexity of a cartesian product is
  // $\theta(n_1n_2 \ldots n_i \ldots n_m)$ where $n_i$ is the size of the ith element.
  def cartesianProduct: List[List[A]] = {
    // Complexity: O(n*p)
    //   where n*m := size of a
    //         p := size of b
    // We ignored flatMap and (++)
    @inline def partialCartesian(
        a: List[List[A]],
        b: List[A]
    ): List[List[A]] = {
      a.flatMap(xs => {
        b.map(y => {
          // FIXME make it constant
          xs ++ List(y)
        })
      })
    }

    xss.headOption match {
      case Some(head) => {
        val tail = xss.tail
        val init = head.map(n => List(n))
        // This complexity is hard to get right
        // since each step increases the size of list a (partialCartesian)
        // by n*(m*o + o) starting from m = 1
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

trait MapSyntax {
  implicit final def toMapOps[K, V](self: Map[K, V]): MapOps[K, V] =
    new MapOps[K, V](self)
}

final class MapOps[K, +V](private val self: Map[K, V]) extends AnyVal {
  // updatedWith can be found in scala 2.13
  def updatedWith[V1 >: V](k: K)(f: Option[V] => Option[V1]): Map[K, V1] = {
    f(self.get(k)) match {
      case Some(v) => self + (k -> v)
      case None    => self - k
    }
  }
}
