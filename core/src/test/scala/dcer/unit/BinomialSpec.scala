package dcer.unit

import dcer.Binomial
import org.scalatest.funspec.AnyFunSpec

import java.math.BigInteger

class BinomialSpec extends AnyFunSpec {
  describe("Binomial") {
    describe("binomialUnsafe") {
      it("should not overflow when n < 62") {
        (1 until 62).foreach { n =>
          (1 to n).foreach { k =>
            val res = BigInteger.valueOf(Binomial.binomialUnsafe(n, k))
            val expected = Binomial.binomialSafe(n, k)
            assert(res == expected, s"n = $n, k = $k")
          }
        }
      }
    }

    describe("binomialThrow") {
      it("should throw on a overflow") {
        val n = 62
        val k = 28
        assertThrows[ArithmeticException](Binomial.binomialThrow(n, k))
      }
    }

    describe("binomialSafe") {
      it("should 'never' overflow") {
        (1 until 1000).foreach { n =>
          val k = n / 2
          assert(true, Binomial.binomialSafe(n, k))
        }
      }
    }
  }
}
