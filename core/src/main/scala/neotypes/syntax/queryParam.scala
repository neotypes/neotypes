package neotypes
package syntax

import mappers.ParameterMapper
import model.query.QueryParam

trait QueryParamSyntax {
  implicit final def neotypesSyntaxQueryParamId[A](a: A): QueryParamIdOps[A] =
    new QueryParamIdOps(a)
}

private[neotypes] final class QueryParamIdOps[A](private val underlying: A) extends AnyVal {
  def asQueryParam(implicit mapper: ParameterMapper[A]): QueryParam =
    mapper.toQueryParam(underlying)
}
