package neotypes
package generic

import query.QueryArgMapper.DerivedQueryParams

import shapeless.Lazy

object implicits {
  implicit def deriveCaseClassQueryParams[P <: Product](implicit mapper: Lazy[CaseClassDerivedQueryParams[P]]): DerivedQueryParams[P] =
    mapper.value
}
