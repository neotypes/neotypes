package neotypes
package generic

import mappers.ResultMapper

import shapeless.{:: => :!:, Coproduct, HList, HNil, LabelledGeneric, Witness}
import shapeless.labelled.FieldType
import shapeless.ops.coproduct.ToHList

import scala.annotation.unused

trait SealedTraitDerivedCoproductInstances[C] extends ResultMapper.DerivedCoproductInstances[C]
object SealedTraitDerivedCoproductInstances {
  implicit final def instance[A, C <: Coproduct, R <: HList](
    implicit @unused gen: LabelledGeneric.Aux[A, C], @unused hlist: ToHList.Aux[C, R], ev: ReprDerivedCoproductInstances[A, R]
  ): SealedTraitDerivedCoproductInstances[A] =
    new SealedTraitDerivedCoproductInstances[A] {
      override final val options: List[(String, ResultMapper[A])] =
        ev.options
    }
}

trait ReprDerivedCoproductInstances[A, R <: HList] extends ResultMapper.DerivedCoproductInstances[A]
object ReprDerivedCoproductInstances {
  implicit final def hnilInstance[A]: ReprDerivedCoproductInstances[A, HNil] =
    new ReprDerivedCoproductInstances[A, HNil] {
      override final val options: List[(String, ResultMapper[A])] =
        List.empty
    }

  implicit final def hconsInstance[A, K <: Symbol, H <: A, T <: HList](
    implicit key: Witness.Aux[K], head: ResultMapper[H], tail: ReprDerivedCoproductInstances[A, T]
  ): ReprDerivedCoproductInstances[A, FieldType[K, H] :!: T] =
    new ReprDerivedCoproductInstances[A, FieldType[K, H] :!: T] {
      override final val options: List[(String, ResultMapper[A])] =
        (key.value.name -> head.widen[A]) :: tail.options
    }
}
