package neotypes
package generic

import mappers.ParameterMapper
import query.QueryArgMapper

import shapeless.{::, Generic, HNil, Lazy}

object semiauto {
  final def deriveCaseClassArgMapper[P <: Product](implicit mapper: Lazy[DerivedCaseClassArgMapper[P]]): QueryArgMapper[P] =
    mapper.value

  final def deriveUnwrappedParameterMapper[A, R](
    implicit gen: Lazy[Generic.Aux[A, R :: HNil]], mapper: ParameterMapper[R]
  ): ParameterMapper[A] =
    mapper.contramap(a => gen.value.to(a).head)
}
