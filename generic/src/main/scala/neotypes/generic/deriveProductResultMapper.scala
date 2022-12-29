package neotypes
package generic

import mappers.ResultMapper

import shapeless.{:: => :!:, HList, HNil, LabelledGeneric, Witness}
import shapeless.labelled.{field => tag, FieldType}

trait CaseClassDerivedProductResultMapper[P <: Product] extends ResultMapper.DerivedProductResultMapper[P]
object CaseClassDerivedProductResultMapper {
  implicit final def instance[P <: Product, R <: HList](
    implicit gen: LabelledGeneric.Aux[P, R], ev: ReprDerivedProductResultMapper[R]
  ): CaseClassDerivedProductResultMapper[P] =
    new CaseClassDerivedProductResultMapper[P] {
      override final val instance: ResultMapper[P] =
        ev.instance.map(gen.from)
    }
}

trait ReprDerivedProductResultMapper[R <: HList] extends ResultMapper.DerivedProductResultMapper[R]
object ReprDerivedProductResultMapper {
  implicit final val hnilInstance: ReprDerivedProductResultMapper[HNil] =
    new ReprDerivedProductResultMapper[HNil] {
      override final val instance: ResultMapper[HNil] =
        ResultMapper.constant(HNil)
    }

  implicit final def hconsInstance[K <: Symbol, H, T <: HList](
    implicit key: Witness.Aux[K], head: ResultMapper[H], tail: ReprDerivedProductResultMapper[T]
  ): ReprDerivedProductResultMapper[FieldType[K, H] :!: T] =
    new ReprDerivedProductResultMapper[FieldType[K, H] :!: T] {
      override final val instance: ResultMapper[FieldType[K, H] :!: T] =
        ResultMapper.field[H](key = key.value.name).and(tail.instance).map {
          case (h, t) =>
            tag[K](h) :: t
        }
    }
}
