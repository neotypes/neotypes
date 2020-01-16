package neotypes
package implicits.syntax

trait StringSyntax {
  implicit final def neotypesSyntaxStringId(s: String): StringIdOps =
    new StringIdOps(s)
}

final class StringIdOps(private val underlying: String) extends AnyVal {
  def query[T]: DeferredQuery[T] =
    DeferredQuery(query = underlying, params = Map.empty)
}
