package dcer.data

import akka.actor.typed.scaladsl.ActorContext
import akka.cluster.Cluster

import scala.util.Try

case class Address(host: String, port: Int) {
  override def toString: String =
    s"$host:$port"
}

object Address {
  def fromCtx(ctx: ActorContext[_]): Address = {
    val cluster = Cluster(ctx.system)
    val host = cluster.selfMember.uniqueAddress.address.host.get
    val port = cluster.selfMember.uniqueAddress.address.port.get
    Address(host, port)
  }

  def parse(str: String): Option[Address] = {
    str.split(':').toList match {
      case host :: port :: Nil => Try(port.toInt).toOption.map(Address(host, _))
      case _                   => None
    }
  }
}

case class ActorAddress(actorName: String, id: Option[Int], address: Address) {
  override def toString: String = {
    id match {
      case Some(id) => s"$actorName-$id-$address"
      case None     => s"$actorName-$address"
    }
  }
}

object ActorAddress {
  def parse(str: String): Option[ActorAddress] = {
    str.split('-').toList match {
      case name :: id :: rawAddress :: Nil =>
        for {
          addr <- Address.parse(rawAddress)
          id <- Try(id.toInt).toOption
        } yield ActorAddress(name, Some(id), addr)

      case name :: rawAddress :: Nil =>
        Address.parse(rawAddress).map(ActorAddress(name, None, _))
      case _ => None
    }
  }
}
