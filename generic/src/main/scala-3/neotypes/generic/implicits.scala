package neotypes
package generic

import neotypes.mappers.ResultMapper.DerivedProductMap

object implicits:

  implicit def deriveCaseClassProductMap[P <: Product](using
    mapper: CaseClassDerivedProductMap[P]
  ): DerivedProductMap[P] =
    mapper
