package neotypes
package generic

import mappers.ResultMapper.{DerivedCoproductInstances, DerivedProductMap}
import query.QueryArgMapper.DerivedQueryParams

import shapeless.Lazy

package object implicits {
  implicit def deriveCaseClassQueryParams[P <: Product](implicit
    queryParams: Lazy[CaseClassDerivedQueryParams[P]]
  ): DerivedQueryParams[P] =
    queryParams.value

  implicit def deriveCaseClassProductMap[P <: Product](implicit
    productMap: Lazy[CaseClassDerivedProductMap[P]]
  ): DerivedProductMap[P] =
    productMap.value

  implicit def deriveSealedTraitCoproductInstances[C](implicit
    instances: Lazy[SealedTraitDerivedCoproductInstances[C]]
  ): DerivedCoproductInstances[C] =
    instances.value
}
