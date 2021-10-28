package dcer.data

import cats.kernel.Semigroup

import scala.Numeric.Implicits._

case class Statistics[T, V: Semigroup: Numeric](value: Map[T, V])
    extends AnyVal {
  def combine(other: Map[T, V]): Statistics[T, V] =
    this + other

  def +(other: Map[T, V]): Statistics[T, V] = {
    import cats.implicits._
    Statistics(this.value |+| other)
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
