package neotypes
package generic

import internal.syntax.either.*
import model.exceptions.ResultMapperException
import mappers.ResultMapper
import mappers.ResultMapper.DerivedProductMap
import model.types.NeoObject

import shapeless3.deriving.*

trait CaseClassDerivedProductMap[P <: Product] extends DerivedProductMap[P]
object CaseClassDerivedProductMap:
  given [P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ResultMapper, P]
  ): CaseClassDerivedProductMap[P] with
    override def map(obj: NeoObject): Either[ResultMapperException, P] =
      val labels = labelled.elemLabels.iterator

      val decodeField =
        [t] => (rm: ResultMapper[t]) => obj.getAs(key = labels.next(), rm)

      val combineFields: Ap[[a] =>> Either[ResultMapperException, a]] =
        [a, b] =>
          (ff: Either[ResultMapperException, a => b], fa: Either[ResultMapperException, a]) =>
            ff.and(fa).map((f, a) => f(a))

      inst.constructA(decodeField)(
        pure = [a] => (x: a) => Right(x),
        map = [a, b] => (fa: Either[ResultMapperException, a], f: a => b) => fa.map(f),
        ap = combineFields
      )
