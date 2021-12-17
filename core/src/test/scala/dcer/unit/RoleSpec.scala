package dcer.unit

import dcer.core.data.Role
import org.scalatest.funspec.AnyFunSpec

class RoleSpec extends AnyFunSpec {
  describe("Role") {
    describe("parse") {
      it("should parse all existing roles") {
        Role.all.foreach { expectedRole =>
          Role.parse(expectedRole.toString) match {
            case None             => fail(s"Failed to parse $expectedRole")
            case Some(actualRole) => assert(actualRole == expectedRole)
          }
        }
      }

      it("should fail parsing an invalid role") {
        val badRole = "bad"
        Role.parse(badRole) match {
          case Some(_) =>
            fail(s"Parsed $badRole as a valid Role but it should have failed.")
          case None => succeed
        }
      }
    }
  }
}
