package neotypes
package syntax

import query.{DeferredQuery, ExecuteQuery, ResultType}

import mappers.ResultMapper

trait StringSyntax {
  implicit final def neotypesSyntaxStringId(s: String): StringIdOps =
    new StringIdOps(s)
}

private[neotypes] final class StringIdOps(private val underlying: String) extends AnyVal {
  def execute: ExecuteQuery =
    new ExecuteQuery(
      query = underlying,
      params = Map.empty
    )

  def query[T](mapper: ResultMapper[T]): DeferredQuery[T, ResultType.Simple.type] =
    new DeferredQuery(
      query = underlying,
      params = Map.empty,
      RT = ResultType.Simple,
      mapper
    )
}
