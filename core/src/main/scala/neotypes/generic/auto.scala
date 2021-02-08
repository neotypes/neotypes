package neotypes.generic

import neotypes.mappers.ResultMapper
import neotypes.CaseClassArgMapper

import shapeless.HList

object auto {

  implicit final def exportHListResultMapper[H <: HList]: Exported[ResultMapper[H]] =
    macro ExportMacros.exportResultMapper[ReprResultMapper, H]

  implicit final def exportProductResultMapper[P <: Product]: Exported[ResultMapper[P]] =
    macro ExportMacros.exportResultMapper[DerivedResultMapper, P]

  implicit final def exportCaseClassArgMapper[P <: Product]: Exported[CaseClassArgMapper[P]] =
    macro ExportMacros.exportCaseClassArgMapper[DerivedCaseClassArgMapper, P]
}
