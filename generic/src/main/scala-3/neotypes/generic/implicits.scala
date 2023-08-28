package neotypes
package generic

import neotypes.mappers.ResultMapper
import neotypes.query.QueryArgMapper
object implicits:
  implicit def deriveCaseClassQueryParams[P <: Product](using
    queryParams: CaseClassDerivedQueryParams[P]
  ): QueryArgMapper.DerivedQueryParams[P] =
    queryParams
  implicit def deriveCaseClassProductMap[P <: Product](using
    mapper: CaseClassDerivedProductMap[P]
  ): ResultMapper.DerivedProductMap[P] =
    mapper
