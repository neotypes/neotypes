package neotypes.generic
package semiauto

import neotypes.mappers.ResultMapper
import shapeless.{HList, Lazy}

private[semiauto] trait AllSemiauto
  extends HListSemiauto
    with ProductSemiauto

private[semiauto] trait HListSemiauto {
  final def deriveHListResultMapper[H <: HList](implicit mapper: Lazy[ReprResultMapper[H]]): ResultMapper[H] =
    mapper.value
}

private[semiauto] trait ProductSemiauto {
  final def deriveProductResultMapper[P <: Product](implicit mapper: Lazy[DerivedResultMapper[P]]): ResultMapper[P] =
    mapper.value
}
