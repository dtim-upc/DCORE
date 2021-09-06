package dcer.data

sealed trait Role
object Role {
  def parse(s: String): Option[Role] = {
    s.toLowerCase match {
      case x if x == Engine.toString.toLowerCase => Some(Engine)
      case x if x == Worker.toString.toLowerCase => Some(Worker)
      case _                                     => None
    }
  }

  def all: List[Role] = List(Engine, Worker)
}

/** NB: do not forget to add the role to [[Role.all]] */
case object Engine extends Role
case object Worker extends Role
