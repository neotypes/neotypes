package neotypes
package generic

import internal.syntax.either._
import mappers.ResultMapper
import model.exceptions.ResultMapperException
import model.types.NeoObject

import shapeless.{:: => :!:, HList, HNil, LabelledGeneric, Witness}
import shapeless.labelled.{field => tag, FieldType}

trait CaseClassDerivedProductMap[P <: Product] extends ResultMapper.DerivedProductMap[P]
object CaseClassDerivedProductMap {
  implicit final def instance[P <: Product, R <: HList](
    implicit gen: LabelledGeneric.Aux[P, R], ev: ReprDerivedProductMap[R]
  ): CaseClassDerivedProductMap[P] =
    new CaseClassDerivedProductMap[P] {
      override def map(obj: NeoObject): Either[ResultMapperException, P] =
        ev.map(obj).map(gen.from)
    }
}

trait ReprDerivedProductMap[R <: HList] extends ResultMapper.DerivedProductMap[R]
object ReprDerivedProductMap {
  implicit final val hnilInstance: ReprDerivedProductMap[HNil] =
    new ReprDerivedProductMap[HNil] {
      override def map(obj: NeoObject): Either[ResultMapperException, HNil] =
        Right(HNil)
    }

  implicit final def hconsInstance[K <: Symbol, H, T <: HList](
    implicit key: Witness.Aux[K], head: ResultMapper[H], tail: ReprDerivedProductMap[T]
  ): ReprDerivedProductMap[FieldType[K, H] :!: T] =
    new ReprDerivedProductMap[FieldType[K, H] :!: T] {
      override def map(obj: NeoObject): Either[ResultMapperException, FieldType[K, H] :!: T] =
        obj.getAs(key = key.value.name, mapper = head).and(tail.map(obj)).map {
          case (h, t) =>
            tag[K](h) :: t
        }
    }
}
