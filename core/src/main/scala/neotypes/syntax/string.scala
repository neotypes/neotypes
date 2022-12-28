package neotypes
package implicits.syntax

import mappers.ResultMapper

trait StringSyntax {
  implicit final def neotypesSyntaxStringId(s: String): StringIdOps =
    new StringIdOps(s)
}

private[neotypes] final class StringIdOps(private val underlying: String) extends AnyVal {
  def execute: ExecuteQuery =
    ExecuteQuery(
      query = underlying,
      params = Map.empty
    )

  def query[T](mapper: ResultMapper[T]): DeferredQuery[T] =
    DeferredQuery(
      query = underlying,
      params = Map.empty,
      mapper
    )
}
