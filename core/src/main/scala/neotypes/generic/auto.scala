package neotypes
package generic

import mappers.ResultMapper

import shapeless.HList

object auto {
  implicit def exportHListResultMapper[H <: HList]: Exported[ResultMapper[H]] =
    macro ExportMacros.exportResultMapper[ReprResultMapper, H]

  implicit def exportProductResultMapper[P <: Product]: Exported[ResultMapper[P]] =
    macro ExportMacros.exportResultMapper[DerivedResultMapper, P]

  implicit def exportCaseClassArgMapper[P <: Product]: Exported[QueryArgMapper[P]] =
    macro ExportMacros.exportCaseClassArgMapper[DerivedCaseClassArgMapper, P]
}
