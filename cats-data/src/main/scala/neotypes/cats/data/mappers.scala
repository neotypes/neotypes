package neotypes
package cats.data

import neotypes.mappers.{KeyMapper, ParameterMapper, ResultMapper}
import neotypes.model.exceptions.IncoercibleException

import _root_.cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.jdk.CollectionConverters._

object mappers extends LowPriorityMappers {
  // Const.
  implicit final def const[A, B](implicit mapper: ResultMapper[A]): ResultMapper[Const[A, B]] =
    mapper.map(Const.apply)

  implicit final def constParameterMapper[A, B](implicit mapper: ParameterMapper[A]): ParameterMapper[Const[A, B]] =
    mapper.contramap(_.getConst)


  // Chain.
  implicit final def chain[A](implicit mapper: ResultMapper[A]): ResultMapper[Chain[A]] =
    ResultMapper.list(mapper).map(Chain.fromSeq)

  implicit final def chainParameterMapper[A](implicit mapper: ParameterMapper[A]): ParameterMapper[Chain[A]] =
    ParameterMapper.fromCast { chain =>
      chain.iterator.map(mapper.toQueryParam).asJava
    }


  // NonEmptyChain.
  implicit final def nonEmptyChain[A](implicit mapper: ResultMapper[A]): ResultMapper[NonEmptyChain[A]] =
    chain(mapper).emap { c =>
      NonEmptyChain.fromChain(c).toRight(
        left = IncoercibleException(
          message = "NonEmptyChain from an empty Chain"
        )
      )
    }

  implicit final def nonEmptyChainParameterMapper[A](implicit mapper: ParameterMapper[A]): ParameterMapper[NonEmptyChain[A]] =
    chainParameterMapper(mapper).contramap(_.toChain)


  // NonEmptyList.
  implicit final def nonEmptyList[A](implicit mapper: ResultMapper[A]): ResultMapper[NonEmptyList[A]] =
    ResultMapper.list(mapper).emap { l =>
      NonEmptyList.fromList(l).toRight(
        left = IncoercibleException(
          message = "NonEmptyList from an empty List"
        )
      )
    }

  implicit final def nonEmptyListParameterMapper[A](implicit mapper: ParameterMapper[List[A]]): ParameterMapper[NonEmptyList[A]] =
    mapper.contramap(_.toList)


  // NonEmptyVector.
  implicit final def nonEmptyVector[A](implicit mapper: ResultMapper[A]): ResultMapper[NonEmptyVector[A]] =
    ResultMapper.vector(mapper).emap { v =>
      NonEmptyVector.fromVector(v).toRight(
        left = IncoercibleException(
          message = "NonEmptyVector from an empty Vector"
        )
      )
    }

  implicit final def nonEmptyVectorParameterMapper[A](implicit mapper: ParameterMapper[Vector[A]]): ParameterMapper[NonEmptyVector[A]] =
    mapper.contramap(_.toVector)


  // NonEmptySet.
  implicit final def nonEmptySet[A](
    implicit mapper: ResultMapper[A], order: Ordering[A]
  ): ResultMapper[NonEmptySet[A]] =
    ResultMapper.collectAs(SortedSet, mapper).emap { s =>
      NonEmptySet.fromSet(s).toRight(
        left = IncoercibleException("NonEmptySet from an empty Set")
      )
    }

  implicit final def nonEmptySetParameterMapper[A](
    implicit mapper: ParameterMapper[SortedSet[A]]
  ): ParameterMapper[NonEmptySet[A]] =
    mapper.contramap(_.toSortedSet)


  // NonEmptyMap.
  implicit final def nonEmptyNeoMap[K, V](
    implicit keyMapper: KeyMapper[K], valueMapper: ResultMapper[V], keyOrder: Ordering[K]
  ): ResultMapper[NonEmptyMap[K, V]] =
    ResultMapper.collectAsNeoMap(SortedMap.sortedMapFactory[K, V], keyMapper, valueMapper).emap { m =>
      NonEmptyMap.fromMap(m).toRight(
        left = IncoercibleException(message = "NonEmptyMap from an empty Map")
      )
    }

  implicit final def nonEmptyNeoMapParameterMapper[K, V](
    implicit mapper: ParameterMapper[SortedMap[K, V]]
  ): ParameterMapper[NonEmptyMap[K, V]] =
    mapper.contramap(_.toSortedMap)
}

private[data] sealed abstract class LowPriorityMappers {
  implicit final def nonEmptyMap[K, V](
    implicit keyMapper: ResultMapper[K], valueMapper: ResultMapper[V], keyOrder: Ordering[K]
  ): ResultMapper[NonEmptyMap[K, V]] =
    ResultMapper.collectAsMap(SortedMap.sortedMapFactory[K, V], keyMapper, valueMapper).emap { m =>
      NonEmptyMap.fromMap(m).toRight(
        left = IncoercibleException(message = "NonEmptyMap from an empty Map")
      )
    }

  implicit final def nonEmptyMapParameterMapper[K, V](
    implicit mapper: ParameterMapper[(K, V)]
  ): ParameterMapper[NonEmptyMap[K, V]] =
    ParameterMapper[Iterable[(K, V)]].contramap(_.toSortedMap)
}
