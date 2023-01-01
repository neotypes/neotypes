package neotypes
package query

import mappers.ParameterMapper
import model.QueryParam

import query.DeferredQueryBuilder

sealed trait QueryArg
object QueryArg {
  final case class Param(param: QueryParam) extends QueryArg
  final case class Params(params: List[(String, QueryParam)]) extends QueryArg
  final case class QueryBuilder(builder: DeferredQueryBuilder) extends QueryArg
}

@annotation.implicitNotFound(
"""
Could not find the ArgMapper for ${A}.

Make sure ${A} is a supported type: https://neotypes.github.io/neotypes/types.html
If ${A} is a case class then `import neotypes.generic.implicits._` for the automated derivation.
"""
)
trait QueryArgMapper[A] {
  def toArg(value: A): QueryArg
}
object QueryArgMapper {
  def apply[A](implicit ev: QueryArgMapper[A]): ev.type = ev

  implicit final def fromParameterMapper[A](
    implicit mapper: ParameterMapper[A]
  ): QueryArgMapper[A] =
    new QueryArgMapper[A] {
      override def toArg(value: A): QueryArg =
        QueryArg.Param(mapper.toQueryParam(value))
    }

  implicit final val deferredQueryBuilderArgMapper: QueryArgMapper[DeferredQueryBuilder] =
    new QueryArgMapper[DeferredQueryBuilder] {
      override def toArg(value: DeferredQueryBuilder): QueryArg =
        QueryArg.QueryBuilder(value)
    }

  trait DerivedQueryParams[A] {
    def getParams(a: A): List[(String, QueryParam)]
  }

  implicit final def fromDerivedQueryParams[A](
    implicit ev: DerivedQueryParams[A]
  ): QueryArgMapper[A] =
    new QueryArgMapper[A] {
      override def toArg(value: A): QueryArg =
        QueryArg.Params(params = ev.getParams(value))
    }
}