package neotypes
package generic

import mappers.{ParameterMapper, ResultMapper, ValueMapper}

import shapeless.{::, Generic, HList, HNil, Lazy}

object semiauto {
  final def deriveHListResultMapper[H <: HList](implicit mapper: Lazy[ReprResultMapper[H]]): ResultMapper[H] =
    mapper.value

  final def deriveProductResultMapper[P <: Product](implicit mapper: Lazy[DerivedResultMapper[P]]): ResultMapper[P] =
    mapper.value

  final def deriveCaseClassArgMapper[P <: Product](implicit mapper: Lazy[DerivedCaseClassArgMapper[P]]): QueryArgMapper[P] =
    mapper.value

  final def deriveUnwrappedParameterMapper[A, R](
    implicit gen: Lazy[Generic.Aux[A, R :: HNil]], mapper: ParameterMapper[R]
  ): ParameterMapper[A] =
    mapper.contramap(a => gen.value.to(a).head)

  final def deriveUnwrappedValueMapper[A, R](
    implicit gen: Lazy[Generic.Aux[A, R :: HNil]], mapper: ValueMapper[R]
  ): ValueMapper[A] =
    mapper.map(r => gen.value.from(r :: HNil))
}
