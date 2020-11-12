package neotypes.generic

import neotypes.mappers.ResultMapper

import shapeless.HList

object auto {

  implicit final def exportHListResultMapper[H <: HList]: Exported[ResultMapper[H]] =
    macro ExportMacros.exportResultMapper[ReprResultMapper, H]

  implicit final def exportProductResultMapper[P <: Product]: Exported[ResultMapper[P]] =
    macro ExportMacros.exportResultMapper[DerivedResultMapper, P]

}
