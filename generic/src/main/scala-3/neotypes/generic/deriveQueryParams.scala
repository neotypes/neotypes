package neotypes
package generic

import mappers.ParameterMapper
import model.query.QueryParam
import query.QueryArgMapper.DerivedQueryParams

import shapeless3.deriving.*

trait CaseClassDerivedQueryParams[P <: Product] extends DerivedQueryParams[P]
object CaseClassDerivedQueryParams:
  given [P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ParameterMapper, P]
  ): CaseClassDerivedQueryParams[P] with
    override def getParams(a: P): List[(String, QueryParam)] =
      labelled
        .elemLabels
        .view
        .zipWithIndex
        .map { case (fieldName, idx) =>
          fieldName -> inst.project(a)(idx) { [t] => (pm: ParameterMapper[t], field: t) =>
            pm.toQueryParam(field)
          }
        }
        .toList
