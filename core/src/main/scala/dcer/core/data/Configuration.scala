package dcer.core.data

import akka.actor.typed.scaladsl.ActorContext
import com.typesafe.config.Config

object Configuration {
  case class Key(value: String) extends AnyVal
  final val WorkersPerNodeKey = Key("dcer.workers-per-node")
  final val WarmUpTimeKey = Key("dcer.warmup-time-seconds")
  final val DistributionStrategyKey = Key("dcer.core.distribution-strategy")
  final val SecondOrderPredicateKey = Key("dcer.second-order-predicate")
  final val QueryPathKey = Key("dcer.query-path")

  class Parser(private val config: Config) {

    def getInt(key: Key): Int = {
      config.getInt(key.value)
    }

    def getString(key: Key): String = {
      config.getString(key.value)
    }

    private def innerGetValue[T](
        key: Key
    )(parse: String => Option[T]): (String, Option[T]) = {
      val str = config.getString(key.value)
      (str, parse(str))
    }

    def getValue[T](key: Key)(parse: String => Option[T]): Option[T] =
      innerGetValue(key)(parse)._2

    def getValueOrThrow[T](key: Key)(parse: String => Option[T]): T = {
      val (str, opt) = innerGetValue[T](key)(parse)
      opt.getOrElse {
        throw new RuntimeException(
          s"""Configuration "${key.value} = $str" failed to parse"""
        )
      }
    }
  }

  def apply(config: Config): Parser = new Parser(config)

  def apply(ctx: ActorContext[_]): Parser = new Parser(
    ctx.system.settings.config
  )
}
