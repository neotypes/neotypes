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

  def query[T](resultMapper: ResultMapper[T]): DeferredQuery[T] =
    DeferredQuery(
      query = underlying,
      params = Map.empty,
      resultMapper
    )

  def readOnlyQuery[T](resultMapper: ResultMapper[T]): ReadOnlyDeferredQuery[T] =
    ReadOnlyDeferredQuery(
      query = underlying,
      params = Map.empty,
      resultMapper
    )
}
