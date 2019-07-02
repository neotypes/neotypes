package neotypes
package implicits.syntax

import mappers.ParameterMapper
import types.QueryParam

import scala.language.implicitConversions

trait QueryParamSyntax {
  implicit final def neotypesSyntaxQueryParamId[A](a: A): QueryParamIdOps[A] =
    new QueryParamIdOps(a)
}

final class QueryParamIdOps[A](private val underlying: A) extends AnyVal {
  def asQueryParam(implicit mapper: ParameterMapper[A]): QueryParam =
    mapper.toQueryParam(underlying)
}

