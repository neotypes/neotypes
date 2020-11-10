package neotypes.generic

import neotypes.mappers.ResultMapper

import shapeless.{HList, Lazy}

object semiauto {

  final def deriveHListResultMapper[H <: HList](implicit mapper: Lazy[ReprResultMapper[H]]): ResultMapper[H] =
    mapper.value

  final def deriveProductResultMapper[P <: Product](implicit mapper: Lazy[DerivedResultMapper[P]]): ResultMapper[P] =
    mapper.value

}
