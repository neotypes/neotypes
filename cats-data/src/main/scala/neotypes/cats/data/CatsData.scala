package neotypes
package cats.data

import internal.utils.traverse.{traverseAs, traverseAsList, traverseAsMap, traverseAsSet, traverseAsVector}
import exceptions.{PropertyNotFoundException, IncoercibleException}
import mappers.{ParameterMapper, ResultMapper, ValueMapper}
import types.QueryParam

import org.neo4j.driver.v1.Value
import _root_.cats.Order
import _root_.cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}
import _root_.cats.instances.string._ // Brings the implicit Order[String] instance into the scope.

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
  implicit final def nonEmptyMapValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[NonEmptyMap[String, T]] =
    new ValueMapper[NonEmptyMap[String, T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptyMap[String, T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAsMap(value.keys.asScala.iterator) { key: String =>
              mapper.to(key, Option(value.get(key))).map { value =>
                key -> value
              }
            }.flatMap { map =>
              NonEmptyMap.fromMap(SortedMap.empty[String, T] ++ map) match {
                case None =>
                  Left(IncoercibleException("NonEmptyMap from an empty map", None.orNull))

                case Some(nonEmptyMap) =>
                  Right(nonEmptyMap)
              }
            }
        }
    }

  implicit final def nonEmptyMapResultMapper[T](implicit mapper: ValueMapper[T]): ResultMapper[NonEmptyMap[String, T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptyMapParameterMapper[T](implicit mapper: ParameterMapper[Map[String, T]]): ParameterMapper[NonEmptyMap[String, T]] =
    mapper.contramap(nonEmptyMap => nonEmptyMap.toSortedMap)


  // NonEmptySet.
  implicit final def nonEmptySetValueMapper[T](implicit mapper: ValueMapper[T], order: Order[T]): ValueMapper[NonEmptySet[T]] =
    new ValueMapper[NonEmptySet[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, NonEmptySet[T]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            traverseAsSet(value.values.asScala.iterator) { v: Value =>
              mapper.to("", Option(v))
            }.flatMap { set =>
              NonEmptySet.fromSet(SortedSet.empty[T](order.toOrdering) | set) match {
                case None =>
                  Left(IncoercibleException("NonEmptySet from an empty list", None.orNull))

                case Some(nonEmptySet) =>
                  Right(nonEmptySet)
              }
            }
        }
    }

  implicit final def nonEmptySetResultMapper[T](implicit mapper: ValueMapper[T], order: Order[T]): ResultMapper[NonEmptySet[T]] =
    ResultMapper.fromValueMapper

  implicit final def nonEmptySetParameterMapper[T](implicit mapper: ParameterMapper[Set[T]]): ParameterMapper[NonEmptySet[T]] =
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
