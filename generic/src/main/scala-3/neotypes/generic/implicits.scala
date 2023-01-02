package neotypes
package generic

import mappers.ResultMapper.{DerivedCoproductInstances, DerivedProductMap}
import query.QueryArgMapper.DerivedQueryParams

object implicits:
  implicit def deriveCaseClassQueryParams[P <: Product](using queryParams: CaseClassDerivedQueryParams[P]): DerivedQueryParams[P] = 
    queryParams

  implicit def deriveCaseClassProductMap[P <: Product](using mapper: CaseClassDerivedProductMap[P]): DerivedProductMap[P] =
    mapper

  implicit def deriveSealedTraitCoproductInstances[C](using instances: SealedTraitDerivedCoproductInstances[C]): DerivedCoproductInstances[C] =
    instances
