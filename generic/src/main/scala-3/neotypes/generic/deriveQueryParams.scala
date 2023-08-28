package neotypes
package generic

import query.QueryArgMapper.DerivedQueryParams
import shapeless3.deriving.*
import neotypes.mappers.ParameterMapper
import neotypes.model.query.QueryParam

trait CaseClassDerivedQueryParams[P <: Product] extends DerivedQueryParams[P]

object CaseClassDerivedQueryParams:
  private type Acc = (List[(String, QueryParam)], Int)

  implicit def CaseClassDerivedQueryParamsInst[P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ParameterMapper, P]
  ): CaseClassDerivedQueryParams[P] =
    new CaseClassDerivedQueryParams[P]:
      override def getParams(a: P): List[(String, QueryParam)] =
        val (m, _) = inst.foldLeft[Acc](a)(List.empty -> 0):
          [t] =>
            (acc: Acc, pm: ParameterMapper[t], x: t) =>
              val (oldM, idx) = acc
              val field = labelled.elemLabels(idx)
              val nm = oldM :+ (field -> pm.toQueryParam(x))
              nm -> (idx + 1)
        m
