package dcer.unit

import dcer.common.data.Port
import org.scalatest.funspec.AnyFunSpec

class PortSpec extends AnyFunSpec {

  describe("Port") {
    describe("Smart constructor") {
      val validPort: Int = 25251
      val invalidPort: Int = -1
      val port = Port.parse(validPort).get

      it("should parse a valid port") {
        assert(port.port == validPort)
      }

      it("should parse a valid string") {
        val port = Port.parse(validPort.toString).get
        assert(port.port == validPort)
      }

      it("should fail to parse an invalid port") {
        val maybePort = Port.parse(invalidPort)
        assert(maybePort.isEmpty)
      }

      it("case class's unapply") {
        assertCompiles(
          """
            |port match { case Port(port) => port }
            |""".stripMargin
        )
      }

      it("companion's apply") {
        assertDoesNotCompile(
          """
            |Port(validPort)
            |""".stripMargin
        )
      }

      it("pub constructor") {
        assertDoesNotCompile(
          """
            |new Port(validPort)
            |""".stripMargin
        )
      }

      it("extends trait") {
        assertDoesNotCompile(
          """
            |new Port {
            |  override def port: Int = validPort
            |}
            |""".stripMargin
        )
      }

      it("case class's copy") {
        assertDoesNotCompile(
          """
            |port.copy(port = validPort)
            |""".stripMargin
        )
      }
    }
  }
}
