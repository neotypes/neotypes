package neotypes
package generic

import mappers.ParameterMapper
import model.query.QueryParam
import query.QueryArgMapper.DerivedQueryParams

import shapeless.{:: => :!:, HList, HNil, LabelledGeneric, Witness}
import shapeless.labelled.FieldType

trait CaseClassDerivedQueryParams[P <: Product] extends DerivedQueryParams[P]
object CaseClassDerivedQueryParams {
  implicit final def instance[P <: Product, R <: HList](
    implicit gen: LabelledGeneric.Aux[P, R], ev: ReprDerivedQueryParams[R]
  ): CaseClassDerivedQueryParams[P] =
    new CaseClassDerivedQueryParams[P] {
      override def getParams(value: P): List[(String, QueryParam)] =
        ev.getParams(gen.to(value))
    }
}

trait ReprDerivedQueryParams[R <: HList] extends DerivedQueryParams[R]
object ReprDerivedQueryParams {
  implicit final val hnilInstance: ReprDerivedQueryParams[HNil] =
    new ReprDerivedQueryParams[HNil] {
      override def getParams(value: HNil): List[(String, QueryParam)] =
        List.empty
    }

  implicit final def hconsInstance[K <: Symbol, H, T <: HList](
    implicit key: Witness.Aux[K], head: ParameterMapper[H], tail: ReprDerivedQueryParams[T]
  ): ReprDerivedQueryParams[FieldType[K, H] :!: T] =
    new ReprDerivedQueryParams[FieldType[K, H] :!: T] {
      override def getParams(value: FieldType[K, H] :!: T): List[(String, QueryParam)] =
        (key.value.name -> head.toQueryParam(value.head)) :: tail.getParams(value.tail)
    }
}
