package dcer.unit

import org.scalatest.funspec.AnyFunSpec

class ImplicitsSpec extends AnyFunSpec {

  import dcer.common.Implicits._

  describe("Implicits") {
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
    describe("MapOps") {
      describe("updatedWith") {
        it("should insert the value if the key is missing") {
          val dict = Map('a' -> 1)
          val result = dict.updatedWith('b')(_ => Some(1))
          val expected = Map('a' -> 1, 'b' -> 1)
          assert(result == expected)
        }
        it("should update the value if the key is present") {
          val key = 'a'
          val dict = Map(key -> 1, 'b' -> 2)
          val result = dict.updatedWith(key)(_.map(_ - 1))
          val expected = Map('a' -> 0, 'b' -> 2)
          assert(result == expected)
        }
        it("should delete the entry if the mapping functions return None") {
          val key = 'a'
          val dict = Map(key -> 1, 'b' -> 2)
          val result = dict.updatedWith(key)(_ => None)
          val expected = Map('b' -> 2)
          assert(result == expected)
        }
      }
    }
  }
}
