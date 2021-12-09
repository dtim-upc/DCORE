package dcer.data

import scala.collection.mutable

/** Directed Acyclic Graph */
final case class DAG[A](
    root: A,
    edges: mutable.MutableList[DAG[A]]
) {
  def map[B](f: A => B): DAG[B] =
    DAG(f(root), edges.map(_.map(f)))

  def flatMap[B](f: A => DAG[B]): DAG[B] = {
    val r = f(root)
    DAG(r.root, r.edges ++ edges.map(_.flatMap(f)))
  }

  def toList: List[A] = root :: edges.toList.flatMap(_.toList)
}

object DAG {
  def single[A](root: A): DAG[A] = DAG(root, mutable.MutableList.empty)
//  def apply[A](root: A, edges: DAG[A]*): DAG[A] =
//    DAG(root, mutable.MutableList(edges: _*))
}
