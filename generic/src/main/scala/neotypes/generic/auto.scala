package neotypes
package generic

import query.QueryArgMapper

object auto {
  implicit def exportCaseClassArgMapper[P <: Product]: Exported[QueryArgMapper[P]] =
    macro ExportMacros.exportCaseClassArgMapper[DerivedCaseClassArgMapper, P]
}
