package dcer.unit

import org.scalatest.funspec.AnyFunSpec

class UtilSpec extends AnyFunSpec {

  import dcer.Implicits._

  describe("Util") {
    describe("ListListOps") {
      it("should return the cartesian product") {
        val xss = List(List(1, 2, 3), List(4, 5), List(6))
        assert(
          xss.cartesianProduct === List(
            List(1, 4, 6),
            List(1, 5, 6),
            List(2, 4, 6),
            List(2, 5, 6),
            List(3, 4, 6),
            List(3, 5, 6)
          )
        )
      }
    }
  }
}
