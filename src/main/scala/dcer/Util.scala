package dcer

import scala.reflect.ClassTag
import scala.util.Try

object Implicits extends AllSyntax

// Class extension is zero-cost.
// The implementation is based on https://github.com/typelevel/cats
trait AllSyntax extends AnySyntax

trait AnySyntax {

  // FIXME
  // java.lang.Object can be disguised as Any
  // but will fail at runtime to find this method
  implicit final def toAnyOps(any: Any): AnyOps =
    new AnyOps(any)
}

final class AnyOps(private val any: Any) extends AnyVal {

  /** Safe cast.
    *
    * Try(v.asInstanceOf[]) will always return Right since [T] is erased
    * at runtime and the compiler translate this into a no-op.
    */
  def safeCast[T](implicit tag: ClassTag[T]): Try[T] =
    Try(tag.runtimeClass.cast(any).asInstanceOf[T])
}
