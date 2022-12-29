package neotypes
package generic

import mappers.ResultMapper.{DerivedCoproductInstances, DerivedProductResultMapper}
import query.QueryArgMapper.DerivedQueryParams

import shapeless.Lazy

object implicits {
  implicit def deriveCaseClassQueryParams[P <: Product](implicit queryParams: Lazy[CaseClassDerivedQueryParams[P]]): DerivedQueryParams[P] =
    queryParams.value

  implicit def deriveCaseClassResultMapper[P <: Product](implicit mapper: Lazy[CaseClassDerivedProductResultMapper[P]]): DerivedProductResultMapper[P] =
    mapper.value

  implicit def deriveCoproductInstances[C](implicit instances: Lazy[SealedTraitDerivedCoproductInstances[C]]): DerivedCoproductInstances[C] =
    instances.value
}
