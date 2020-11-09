package neotypes.generic
package auto

import neotypes.mappers.ResultMapper
import shapeless.HList

private[auto] trait AllAuto
  extends HListAuto
    with ProductAuto

private[auto] trait HListAuto {
  implicit final def exportHListResultMapper[H <: HList]: Exported[ResultMapper[H]] =
    macro ExportMacros.exportResultMapper[ReprResultMapper, H]
}

private[auto] trait ProductAuto {
  implicit final def exportProductResultMapper[P <: Product]: Exported[ResultMapper[P]] =
    macro ExportMacros.exportResultMapper[DerivedResultMapper, P]
}
