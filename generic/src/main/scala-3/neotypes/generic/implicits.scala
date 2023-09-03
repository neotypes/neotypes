package neotypes
package generic

import neotypes.mappers.ResultMapper
import neotypes.query.QueryArgMapper
object implicits:
  inline implicit def deriveCaseClassQueryParams[P <: Product](using
    inline queryParams: CaseClassDerivedQueryParams[P]
  ): QueryArgMapper.DerivedQueryParams[P] =
    queryParams

  inline implicit def deriveCaseClassProductMap[P <: Product](using
    inline mapper: CaseClassDerivedProductMap[P]
  ): ResultMapper.DerivedProductMap[P] =
    mapper

  inline implicit def deriveSealedTraitCoproductInstances[C](using
    inline instances: SealedTraitDerivedCoproductInstances[C]
  ): ResultMapper.DerivedCoproductInstances[C] =
    instances
