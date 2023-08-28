package neotypes
package generic

import neotypes.mappers.ResultMapper
import neotypes.mappers.ResultMapper.DerivedProductMap
import neotypes.model.types.NeoObject
import neotypes.model.exceptions.ResultMapperException
import neotypes.model.exceptions.IncoercibleException

import shapeless3.deriving.*

trait CaseClassDerivedProductMap[P <: Product] extends DerivedProductMap[P]
object CaseClassDerivedProductMap:
  private type Acc = (Int, NeoObject, Option[ResultMapperException])
  implicit def CaseClassDerivedProductMapInst[P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ResultMapper, P]
  ): CaseClassDerivedProductMap[P] = new CaseClassDerivedProductMap[P]:
    override def map(obj: NeoObject): Either[ResultMapperException, P] =
      // `unfold` traverses over Product field types.
      // For example, `inst.unfold` for `case class A(i:Int, s: String)`
      // traverses over `Int` and `String`
      val ((_, _, decodeOpt), maybeDerived) = inst.unfold[Acc]((0, obj, None)):
        [t] =>
          (acc: Acc, mapper: ResultMapper[t]) =>
            val (idx, remaining, optErr) = acc
            val fieldName = labelled.elemLabels(idx)
            val decodedHead = obj.getAs(fieldName, mapper)
            (
              (idx + 1, obj, decodedHead.left.toOption),
              // terminate when this is `None`, `None` means `ResultMapper[t]` fails to decode field of type `t`
              decodedHead.toOption
          )
      maybeDerived.toRight(
        decodeOpt.getOrElse(
          IncoercibleException(
            "Unexpected exception occurred during derivation.",
            None
          )
        )
      )
