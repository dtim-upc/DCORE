package dcer.core.data

import org.scalatest.funspec.AnyFunSpec

class StatisticsSpec extends AnyFunSpec {
  import StatisticsSpec._

  describe("Statistics") {
    it("should combine statistics") {
      val stats1: Statistics[Char, Int] = Statistics(
        Map(
          'a' -> 50,
          'b' -> 20
        )
      )
      val stats2: Statistics[Char, Int] = Statistics(
        Map(
          'b' -> 45,
          'c' -> 150
        )
      )
      val res: Statistics[Char, Int] = stats1 + stats2
      val expected: Statistics[Char, Int] = Statistics(
        Map(
          'a' -> 50,
          'b' -> 65,
          'c' -> 150
        )
      )
      assert(res == expected)
    }

    it("should compute the mean") {
      val stats = getStats()
      assert(round2(stats.mean()) == 100.0)
    }

    it("should compute the variance") {
      val stats = getStats()
      assert(round2(stats.variance()) == 1666.67)
    }

    it("should compute the standard deviation") {
      val stats = getStats()
      assert(round2(stats.stdDev()) == 40.82)
    }

    it("should compute the coefficient of variation (CV)") {
      val stats = getStats()
      assert(round2(stats.coefficientOfVariation()) == 0.41)
    }
  }
}

object StatisticsSpec {
  def round(scale: Int)(d: Double): Double = {
    BigDecimal(d).setScale(scale, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def round2(d: Double): Double = round(scale = 2)(d)

  def getStats(): Statistics[Char, Int] = {
    Statistics(
      Map(
        'a' -> 100,
        'b' -> 50,
        'c' -> 150
      )
    )
  }

}
