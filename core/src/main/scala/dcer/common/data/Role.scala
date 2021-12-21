package dcer.common.data

sealed trait Role
object Role {
  def parse(s: String): Option[Role] = {
    s.toLowerCase match {
      case x if x == Master.toString.toLowerCase => Some(Master)
      case x if x == Slave.toString.toLowerCase  => Some(Slave)
      case _                                     => None
    }
  }

  def all: List[Role] = List(Master, Slave)
}

/** NB: do not forget to add the role to [[Role.all]] */
case object Master extends Role
case object Slave extends Role
