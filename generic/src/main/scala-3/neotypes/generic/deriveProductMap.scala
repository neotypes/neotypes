package neotypes
package generic

import neotypes.mappers.ResultMapper
import neotypes.mappers.ResultMapper.DerivedProductMap
import neotypes.model.types.NeoObject
import neotypes.model.exceptions.ResultMapperException

import shapeless3.deriving.*

trait CaseClassDerivedProductMap[P <: Product] extends DerivedProductMap[P]
object CaseClassDerivedProductMap:
  given [P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ResultMapper, P]
  ): CaseClassDerivedProductMap[P] with
    override def map(obj: NeoObject): Either[ResultMapperException, P] =
      import neotypes.internal.syntax.either.*
      val labels = labelled.elemLabels.iterator
      val decodeField = [t] =>
        (resolvedMapper: ResultMapper[t]) =>
          for
            field <- Right(labels.next())
            value <- obj.getAs(field, resolvedMapper)
          yield value

      inst.constructA(decodeField)(
        [t] => (x: t) => Right(x),
        [t, s] => (fa: Either[ResultMapperException, t], f: t => s) => fa.map(f),
        [t, s] =>
          (ff: Either[ResultMapperException, t => s], fa: Either[ResultMapperException, t]) => ff.and(fa).map(_(_))
      )
