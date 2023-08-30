package neotypes
package generic

import query.QueryArgMapper.DerivedQueryParams
import shapeless3.deriving.*
import neotypes.mappers.ParameterMapper
import neotypes.model.query.QueryParam

trait CaseClassDerivedQueryParams[P <: Product] extends DerivedQueryParams[P]

object CaseClassDerivedQueryParams:
  private type Acc = (List[(String, QueryParam)], Int)

  given [P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ParameterMapper, P]
  ): CaseClassDerivedQueryParams[P] with
    override def getParams(a: P): List[(String, QueryParam)] =
      val (m, _) = inst.foldLeft[Acc](a)(List.empty -> 0):
        [t] =>
          (acc: Acc, pm: ParameterMapper[t], x: t) =>
            val (oldM, idx) = acc
            val field = labelled.elemLabels(idx)
            val nm = (field -> pm.toQueryParam(x)) :: oldM
            nm -> (idx + 1)
      m.reverse
