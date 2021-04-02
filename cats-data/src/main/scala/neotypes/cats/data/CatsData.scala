package neotypes
package cats.data

import exceptions.{PropertyNotFoundException, IncoercibleException}
import internal.utils.traverse._
import mappers.{KeyMapper, ParameterMapper, ResultMapper, ValueMapper}

import org.neo4j.driver.Value
import _root_.cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}

import scala.collection.compat._
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.jdk.CollectionConverters._

trait CatsData {
  // Chain.
  private final def traverseAsChain[A, B](iter: Iterator[A])
                                         (f: A => Either[Throwable, B]): Either[Throwable, Chain[B]] = {
    @annotation.tailrec
    def loop(acc: Chain[B]): Either[Throwable, Chain[B]] =
      if (iter.hasNext) f(iter.next()) match {
        case Right(value) => loop(acc = acc :+ value)
        case Left(e)      => Left(e)
      } else {
        Right(acc)
      }
    loop(acc = Chain.nil)
  }

  implicit final def chainValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[Chain[T]] =
    new ValueMapper[Chain[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Chain[T]] =
        value match {
          case None =>
            Right(Chain.nil)

          case Some(value) =>
            traverseAsChain(value.values.asScala.iterator) { v: Value =>
              mapper.to("", Option(v))
            }
        }
    }

  implicit final def chainResultMapper[T](implicit mapper: ValueMapper[T]): ResultMapper[Chain[T]] =
    ResultMapper.fromValueMapper

  implicit final def chainParameterMapper[T](implicit mapper: ParameterMapper[List[T]]): ParameterMapper[Chain[T]] =
    mapper.contramap(chain => chain.toList)


  // Const.
  implicit final def constvalueMapper[A, B](implicit mapper: ValueMapper[A]): ValueMapper[Const[A, B]] =
    mapper.map(a => Const(a))

  implicit final def constResultMapper[A, B](implicit mapper: ValueMapper[A]): ResultMapper[Const[A, B]] =
    ResultMapper.fromValueMapper

  implicit final def constParameterMapper[A, B](implicit mapper: ParameterMapper[A]): ParameterMapper[Const[A, B]] =
    mapper.contramap(const => const.getConst)


  // NonEmptyChain.
  implicit final def nonEmptyChainValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[NonEmptyChain[T]] =
    new ValueMapper[NonEmptyChain[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptyChain[T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAsChain(value.values.asScala.iterator) { v: Value =>
              mapper.to("", Option(v))
            }.flatMap { chain =>
              NonEmptyChain.fromChain(chain) match {
                case None =>
                  Left(IncoercibleException("NonEmptyChain from an empty list", None.orNull))

                case Some(nonEmptyChain) =>
                  Right(nonEmptyChain)
              }
            }
        }
    }

  implicit final def nonEmptyChainResultMapper[T](implicit mapper: ValueMapper[T]): ResultMapper[NonEmptyChain[T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptyChainParameterMapper[T](implicit mapper: ParameterMapper[List[T]]): ParameterMapper[NonEmptyChain[T]] =
    mapper.contramap(nonEmptyChain => nonEmptyChain.toChain.toList)


  // NonEmptyList.
  implicit final def nonEmptyListValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[NonEmptyList[T]] =
    new ValueMapper[NonEmptyList[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptyList[T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAsList(value.values.asScala.iterator) { v: Value =>
              mapper.to("", Option(v))
            }.flatMap { list =>
              NonEmptyList.fromList(list) match {
                case None =>
                  Left(IncoercibleException("NonEmptyList from an empty list", None.orNull))

                case Some(nonEmptyList) =>
                  Right(nonEmptyList)
              }
            }
        }
    }

  implicit final def nonEmptyListResultMapper[T](implicit mapper: ValueMapper[T]): ResultMapper[NonEmptyList[T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptyListParameterMapper[T](implicit mapper: ParameterMapper[List[T]]): ParameterMapper[NonEmptyList[T]] =
    mapper.contramap(nonEmptyList => nonEmptyList.toList)


  // NonEmptyMap.
  implicit final def nonEmptyMapValueMapper[K, V](
    implicit order: Ordering[K], keyMapper: KeyMapper[K], mapper: ValueMapper[V]
  ): ValueMapper[NonEmptyMap[K, V]] =
    new ValueMapper[NonEmptyMap[K, V]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptyMap[K, V]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAs(SortedMap : Factory[(K, V), SortedMap[K, V]])(value.keys.asScala.iterator) { key =>
              for {
                v <- mapper.to(fieldName = key, Option(value.get(key)))
                k <- keyMapper.decodeKey(key)
              } yield k -> v
            } flatMap { map =>
              NonEmptyMap.fromMap(map).toRight(
                left = IncoercibleException("NonEmptyMap from an empty map", None.orNull)
              )
            }
        }
    }

  implicit final def nonEmptyMapResultMapper[K, V](
    implicit oder: Ordering[K], keyMapper: KeyMapper[K], valueMapper: ValueMapper[V]
  ): ResultMapper[NonEmptyMap[K, V]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptyMapParameterMapper[K, V](
    implicit mapper: ParameterMapper[Map[K, V]]
  ): ParameterMapper[NonEmptyMap[K, V]] =
    mapper.contramap(nonEmptyMap => nonEmptyMap.toSortedMap)


  // NonEmptySet.
  implicit final def nonEmptySetValueMapper[T](
    implicit order: Ordering[T], mapper: ValueMapper[T]
  ): ValueMapper[NonEmptySet[T]] =
    new ValueMapper[NonEmptySet[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptySet[T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAs(SortedSet : Factory[T, SortedSet[T]])(value.values.asScala.iterator) { v =>
              mapper.to(fieldName = "", Option(v))
            } flatMap { set =>
              NonEmptySet.fromSet(set).toRight(
                left = IncoercibleException("NonEmptySet from an empty list", None.orNull)
              )
            }
        }
    }

  implicit final def nonEmptySetResultMapper[T](
    implicit order: Ordering[T], mapper: ValueMapper[T]
  ): ResultMapper[NonEmptySet[T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptySetParameterMapper[T](
    implicit mapper: ParameterMapper[Set[T]]
  ): ParameterMapper[NonEmptySet[T]] =
    mapper.contramap(nonEmptySet => nonEmptySet.toSortedSet)


  // NonEmptyVector.
  implicit final def nonEmptyVectorValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[NonEmptyVector[T]] =
    new ValueMapper[NonEmptyVector[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptyVector[T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAsVector(value.values.asScala.iterator) { v: Value =>
              mapper.to("", Option(v))
            }.flatMap { vector =>
              NonEmptyVector.fromVector(vector) match {
                case None =>
                  Left(IncoercibleException("NonEmptyVector from an empty list", None.orNull))

                case Some(nonEmptyVector) =>
                  Right(nonEmptyVector)
              }
            }
        }
    }

  implicit final def nonEmptyVectorResultMapper[T](implicit mapper: ValueMapper[T]): ResultMapper[NonEmptyVector[T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptyVectorParameterMapper[T](implicit mapper: ParameterMapper[Vector[T]]): ParameterMapper[NonEmptyVector[T]] =
    mapper.contramap(nonEmptyVector => nonEmptyVector.toVector)
}
