package neotypes
package generic

import mappers.ResultMapper

import shapeless3.deriving.*
import neotypes.model.types.NeoObject
import neotypes.model.exceptions.{ResultMapperException, IncoercibleException}

trait CaseClassDerivedProductMap[P <: Product] extends ResultMapper.DerivedProductMap[P]

object CaseClassDerivedProductMap:
  type Acc = (Int, NeoObject, Option[ResultMapperException])
  given [P <: Product](using labelled: Labelling[P], inst: K0.ProductInstances[ResultMapper, P]): ResultMapper.DerivedProductMap[P] =
    new ResultMapper.DerivedProductMap[P] {
      override def map(obj: NeoObject): Either[ResultMapperException, P] =
        val ((_, _, decodeOpt), resultOpt) = inst.unfold[Acc]((0, obj, None)) {
          [t] => (acc: Acc, mapper: ResultMapper[t]) => {
            val (idx, remaining, optErr) = acc
            val fieldName = labelled.elemLabels(idx)
            val decodedHead = obj.getAs(fieldName)(mapper)
            ((idx + 1, obj, decodedHead.left.toOption), decodedHead.toOption)
          }
        }
        resultOpt.toRight(decodeOpt.getOrElse(IncoercibleException("Unexpected exception occurred during derivation.", None)))

    }