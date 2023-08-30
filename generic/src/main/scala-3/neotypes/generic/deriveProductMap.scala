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
  private type Acc = (Int, Option[ResultMapperException])
  given [P <: Product](using
    labelled: Labelling[P],
    inst: K0.ProductInstances[ResultMapper, P]
  ): CaseClassDerivedProductMap[P] with
    override def map(obj: NeoObject): Either[ResultMapperException, P] =
      // `unfold` traverses over Product field types.
      // For example, `inst.unfold` for `case class A(i:Int, s: String)`
      // traverses over `Int` and `String`.
      // When all results are `Some` value of type `t`, `inst.unfold`
      // maps these values into Product type.
      val ((_, maybeError), maybeDerived) = inst.unfold[Acc]((0, None)):
        [t] =>
          (acc: Acc, resolvedInstance: ResultMapper[t]) =>
            val (idx, _) = acc
            val fieldName = labelled.elemLabels(idx)
            val decodedField = obj.getAs(fieldName, resolvedInstance)
            decodedField match
              case Right(field) =>
                // continue
                ((idx + 1, None), Some(field))

              case Left(failure) =>
                // terminate when the second elmement is `None`, `None` means
                // `ResultMapper[t]` fails to decode a field of type `t`
                ((idx + 1, Some(failure)), None)

      (maybeDerived, maybeError) match
        case (Some(productType), None) =>
          Right(productType)

        case (None, Some(error)) =>
          Left(error)

        case (Some(_), Some(_)) | (None, None) =>
          Left(
            IncoercibleException(
              "Unexpected exception occurred during derivation.",
              None
            )
          )
