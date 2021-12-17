package dcer.core.data

import cats.kernel.Semigroup

import scala.Numeric.Implicits._

/*
We could make the class independent of the type classes and
each operation to require the type classes for that operation.
- pro: we can delegate the implicits to each operation.
- cons: each operation needs to have the implicits in scope.
 */
case class Statistics[T, V: Semigroup: Numeric](value: Map[T, V]) {
  def combine(other: Statistics[T, V]): Statistics[T, V] =
    this + other

  def +(other: Statistics[T, V]): Statistics[T, V] = {
    import cats.implicits._
    Statistics(this.value |+| other.value)
  }

  def mean(): Double = {
    val xs = value.values
    xs.sum.toDouble / xs.size
  }

  def variance(): Double = {
    val xs = value.values
    val avg = this.mean()
    xs.map(_.toDouble).map(a => math.pow(a - avg, 2)).sum / xs.size
  }

  def stdDev(): Double = {
    math.sqrt(this.variance())
  }

  def coefficientOfVariation(): Double = {
    val stdDev = this.stdDev()
    val mean = this.mean()
    stdDev / mean
  }
}

object Statistics {}
