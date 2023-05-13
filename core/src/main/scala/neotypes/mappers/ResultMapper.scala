package neotypes
package mappers

import boilerplate.BoilerplateResultMappers
import internal.syntax.either._
import internal.utils.traverseAs
import model.exceptions.{ChainException, IncoercibleException, PropertyNotFoundException, ResultMapperException}
import model.types._

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{Duration => JDuration, LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, Period => JPeriod, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.UUID
import scala.collection.Factory
import scala.collection.immutable.ArraySeq
import scala.concurrent.duration.{FiniteDuration => SDuration}
import scala.reflect.ClassTag
import scala.util.Try

/** Allows decoding a [[NeoType]] into a value of type [[A]]. */
@annotation.implicitNotFound(
"""
Could not find the ResultMapper for "${A}".

Make sure ${A} is a supported type: https://neotypes.github.io/neotypes/types.html
If ${A} is a case class or a sealed trait,
then `import neotypes.generic.implicits._` to enable automatic derivation.
"""
)
trait ResultMapper[A] { self =>
  /** Attempts to decode the given [[NeoType]],
    * may fail with a [[ResultMapperException]].
    */
  def decode(value: NeoType): Either[ResultMapperException, A]

  /** Chains another mapper based on decoding result of this one. */
  final def flatMap[B](f: A => ResultMapper[B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).flatMap { t =>
        f(t).decode(value)
      }
    }

  /** Chains a transformation function that can manage failures. */
  final def transform[B](f: Either[ResultMapperException, A] => Either[ResultMapperException, B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      f(self.decode(value))
    }

  /** Chains a transformation function that can fail. */
  final def emap[B](f: A => Either[ResultMapperException, B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).flatMap(f)
    }

  /** Chains a transformation function that can not fail. */
  final def map[B](f: A => B): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).map(f)
    }

  /** Combines the result of another mapper with this one.
    * In case both fail, the errors will be merged into a [[model.exceptions.ChainException]].
    */
  final def and[B](other: ResultMapper[B]): ResultMapper[(A, B)] =
    ResultMapper.instance { value =>
      self.decode(value) and other.decode(value)
    }

  /** Chains a mapper as a fallback when this one fails.
    * In case both fail, the errors will be merged into a [[model.exceptions.ChainException]].
    */
  final def or[B >: A](other: ResultMapper[B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).left.flatMap { thisEx =>
        other.decode(value).left.map { otherEx =>
          ChainException.from(
            exceptions = thisEx, otherEx
          )
        }
      }
    }

  /** Used to emulate covariance subtyping. */
  final def widen[B >: A]: ResultMapper[B] =
    self.asInstanceOf[ResultMapper[B]]
}

object ResultMapper extends ResultMappersLowPriority0 with BoilerplateResultMappers {
  /** Allows materializing an implicit [[ResultMapper]] as an explicit value. */
  def apply[A](implicit mapper: ResultMapper[A]): ResultMapper[A] =
    mapper

  /** Factory to create a [[ResultMapper]] from a decoding function. */
  def instance[A](f: NeoType => Either[ResultMapperException, A]): ResultMapper[A] =
    new ResultMapper[A] {
      override def decode(value: NeoType): Either[ResultMapperException, A] =
        f(value)
    }

  /** Creates a [[ResultMapper]] that will always decode to the given value,
    * no matter the actual input passed to `decode`.
    */
  def constant[A](a: A): ResultMapper[A] =
    instance(_ => Right(a))

  /** Creates a [[ResultMapper]] that will always fail with the given [[ResultMapperException]],
    * no matter the actual input passed to `decode`.
    */
  def failed[A](ex: ResultMapperException): ResultMapper[A] =
    instance(_ => Left(ex))

  /** Factory to create a [[ResultMapper]] from a decoding function,
    * emulating pattern matching.
    */
  def fromMatch[A](
    pf: PartialFunction[NeoType, Either[ResultMapperException, A]]
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] = {
    val singletonRecordFallback: PartialFunction[NeoType, NeoType] = {
      case NeoMap(values) if (values.size == 1) =>
        values.head._2
    }

    val fail: PartialFunction[NeoType, Either[ResultMapperException, A]] = {
      case value =>
        Left(IncoercibleException(s"Couldn't decode ${value} into a ${ct.runtimeClass.getSimpleName}"))
    }

    instance(singletonRecordFallback.andThen(pf) orElse pf orElse fail)
  }

  /** Factory to create a [[ResultMapper]] from a decoding function,
    * emulating pattern matching.
    */
  def fromMatch[A](
    pf: PartialFunction[NeoType, A]
  ) (
    implicit ct: ClassTag[A], ev: DummyImplicit
  ): ResultMapper[A] =
    fromMatch(pf.andThen(Right.apply _))

  /** Factory to create a [[ResultMapper]] from a decoding function over numeric values,
    * emulating pattern matching.
    */
  def fromNumeric[A](
    f: Value.NumberValue => A
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] = fromMatch {
    case value: Value.NumberValue =>
      f(value)
  }

  /** Factory to create a [[ResultMapper]] from a decoding function over temporal values,
    * emulating pattern matching.
    */
  def fromTemporalInstant[A](
    pf: PartialFunction[Value.TemporalInstantValue, A]
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] = {
    val matchTemporalInstant: PartialFunction[NeoType, Value.TemporalInstantValue] = {
      case value: Value.TemporalInstantValue => value
    }

    fromMatch(matchTemporalInstant andThen pf)
  }

  /** Passthrough [[ResultMapper]], does not apply any decoding logic. */
  implicit final val identity: ResultMapper[NeoType] =
    instance(value => Right(value))

  /** Passthrough [[ResultMapper]], ignores the input. */
  implicit final val ignore: ResultMapper[Unit] =
    instance(_ => Right(()))

  /** [[ResultMapper]] that will decode any numeric value into an [[Int]], may lose precision. */
  implicit final val int: ResultMapper[Int] = fromNumeric {
    case Value.Integer(value) =>
      value.toInt

    case Value.Decimal(value) =>
      value.toInt
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Short]], may lose precision. */
  implicit final val short: ResultMapper[Short] = fromNumeric {
    case Value.Integer(value) =>
      value.toShort

    case Value.Decimal(value) =>
      value.toShort
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Byte]], may lose precision. */
  implicit final val byte: ResultMapper[Byte] = fromNumeric {
    case Value.Integer(value) =>
      value.toByte

    case Value.Decimal(value) =>
      value.toByte
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Long]], may lose precision. */
  implicit final val long: ResultMapper[Long] = fromNumeric {
    case Value.Integer(value) =>
      value

    case Value.Decimal(value) =>
      value.toLong
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Float]], may lose precision. */
  implicit final val float: ResultMapper[Float] = fromNumeric {
    case Value.Integer(value) =>
      value.toFloat

    case Value.Decimal(value) =>
      value.toFloat
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Double]]. */
  implicit final val double: ResultMapper[Double] = fromNumeric {
    case Value.Integer(value) =>
      value.toDouble

    case Value.Decimal(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[BigInt]]. */
  implicit final val bigInt: ResultMapper[BigInt] = fromMatch {
    case Value.Integer(value) =>
      BigInt(value)

    case Value.Str(value) =>
      BigInt(value)

    case Value.Bytes(value) =>
      BigInt(value.unsafeArray.asInstanceOf[Array[Byte]])
  }

  /** [[ResultMapper]] that will decode the input as a [[BigDecimal]]. */
  implicit final val bigDecimal: ResultMapper[BigDecimal] = fromMatch {
    case Value.Integer(value) =>
      BigDecimal(value)

    case Value.Decimal(value) =>
      BigDecimal(value)

    case Value.Str(value) =>
      BigDecimal(value)

    case Value.Bytes(value) =>
      BigDecimal(BigInt(value.unsafeArray.asInstanceOf[Array[Byte]]))
  }

  /** [[ResultMapper]] that will decode the input as a [[Boolean]]. */
  implicit final val boolean: ResultMapper[Boolean] = fromMatch {
    case Value.Bool(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[String]]. */
  implicit final val string: ResultMapper[String] = fromMatch {
    case Value.Str(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[UUID]]. */
  implicit final val uuid: ResultMapper[UUID] = fromMatch {
    case Value.Str(value) =>
      Try(UUID.fromString(value)).toEither.left.map { ex =>
        IncoercibleException(
          message = s"Couldn't create an UUID from ${value}",
          cause = Some(ex)
        )
      }

    case Value.Bytes(value) =>
      Try(UUID.nameUUIDFromBytes(value.unsafeArray.asInstanceOf[Array[Byte]])).toEither.left.map { ex =>
        IncoercibleException(
          message = s"Couldn't create an UUID from ${value}",
          cause = Some(ex)
        )
      }
  }

  /** [[ResultMapper]] that will decode the input as a byte array. */
  implicit final val bytes: ResultMapper[ArraySeq[Byte]] = fromMatch {
    case Value.Bytes(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[Node]]. */
  implicit final val node: ResultMapper[Node] = fromMatch {
    case value: Node =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[Relationship]]. */
  implicit final val relationship: ResultMapper[Relationship] = fromMatch {
    case value: Relationship =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[Path]]. */
  implicit final val path: ResultMapper[Path] = fromMatch {
    case value: Path =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[NeoPoint]]. */
  implicit final val neoPoint: ResultMapper[NeoPoint] = fromMatch {
    case Value.Point(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[NeoDuration]]. */
  implicit final val neoDuration: ResultMapper[NeoDuration] = fromMatch {
    case Value.Duration(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[JDuration]]. */
  implicit final val javaDuration: ResultMapper[JDuration] =
    neoDuration.map { isoDuration =>
      JDuration
        .ZERO
        .plusDays(isoDuration.days)
        .plusSeconds(isoDuration.seconds)
        .plusNanos(isoDuration.nanoseconds.toLong)
    }

  /** [[ResultMapper]] that will decode the input as a [[JPeriod]]. */
  implicit final val javaPeriod: ResultMapper[JPeriod] =
    neoDuration.map { isoDuration =>
      JPeriod
        .ZERO
        .plusMonths(isoDuration.months)
        .plusDays(isoDuration.days)
    }

  /** [[ResultMapper]] that will decode the input as a [[SDuration]]. */
  implicit final val scalaDuration: ResultMapper[SDuration] =
    javaDuration.map(d => scala.concurrent.duration.Duration.fromNanos(d.toNanos))

  /** [[ResultMapper]] that will decode any temporal-instant value into an [[JDate]]. */
  implicit final val javaLocalDate: ResultMapper[JDate] = fromTemporalInstant {
    case Value.LocalDate(value) =>
      value

    case Value.LocalDateTime(value) =>
      value.toLocalDate

    case Value.ZonedDateTime(value) =>
      value.toLocalDate
  }

  /** [[ResultMapper]] that will decode any temporal-instant value into an [[JTime]]. */
  implicit final val javaLocalTime: ResultMapper[JTime] = fromTemporalInstant {
    case Value.LocalTime(value) =>
      value

    case Value.LocalDateTime(value) =>
      value.toLocalTime

    case Value.ZonedTime(value) =>
      value.toLocalTime

    case Value.ZonedDateTime(value) =>
      value.toLocalTime
  }

  /** [[ResultMapper]] that will decode any temporal-instant value into an [[JDateTime]]. */
  implicit final val javaLocalDateTime: ResultMapper[JDateTime] = fromTemporalInstant {
    case Value.LocalDateTime(value) =>
      value

    case Value.ZonedDateTime(value) =>
      value.toLocalDateTime
  }

  /** [[ResultMapper]] that will decode any temporal-instant value into an [[JZTime]]. */
  implicit final val javaOffsetTime: ResultMapper[JZTime] = fromTemporalInstant {
    case Value.ZonedTime(value) =>
      value

    case Value.ZonedDateTime(value) =>
      value.toOffsetDateTime.toOffsetTime
  }

  /** [[ResultMapper]] that will decode any temporal-instant value into an [[JZDateTime]]. */
  implicit final val javaZonedDateTime: ResultMapper[JZDateTime] = fromTemporalInstant {
    case Value.ZonedDateTime(value) =>
      value
  }

  /** [[ResultMapper]] that will decode the input as a [[NeoObject]]. */
  implicit final val neoObject: ResultMapper[NeoObject] = fromMatch {
    case value: NeoObject =>
      value
  }

  /** [[ResultMapper]] that will decode the input as an heterogeneous list of [[NeoType]] values. */
  implicit final val values: ResultMapper[List[NeoType]] = fromMatch {
    case NeoList(values) =>
      values

    case value: NeoObject =>
      value.values

    case Value.ListValue(values) =>
      values
  }

  /** [[ResultMapper]] that will decode the input as fixed size heterogeneous list of [[NeoType]] values.
    *
    * @note Pads with [[Value.NullValue]] in case it is necessary.
    * @see [[values]]
    */
  def take(n: Int): ResultMapper[List[NeoType]] =
    values.map { col =>
      (col.iterator ++ Iterator.continually(Value.NullValue)).take(n).toList
    }

  /** Creates a [[ResultMapper]] that will try to decode
    * the first / single element list of values,
    * using the provided mapper.
    *
    * @note will fail if the values are empty,
    * but won't fail if they have more than one element.
    */
  def loneElement[A](mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      col.headOption match {
        case Some(a) =>
          mapper.decode(a)

        case None =>
          Left(IncoercibleException("Values was empty"))
      }
    }

  /** Creates a new [[ResultMapper]] from a base one,
    * which will recover from `null` values into a `None`.
    */
  implicit final def option[A](
    implicit
    mapper: ResultMapper[A]
  ): ResultMapper[Option[A]] = fromMatch {
    case Value.NullValue =>
      Right(Option.empty[A])

    case value =>
      mapper.decode(value).map(Some.apply)
  }

  /** Creates a [[ResultMapper]] that will attempt to decode the input using the two provided mappers. */
  implicit final def either[A, B](
    implicit
    a: ResultMapper[A],
    b: ResultMapper[B]
  ): ResultMapper[Either[A, B]] =
    a.map(Left.apply).or(b.map(Right.apply))

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[List]],
    * using the provided mapper to decode each element.
    **/
  def list[A](mapper: ResultMapper[A]): ResultMapper[List[A]] =
    collectAs(List, mapper)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[Vector]],
    * using the provided mapper to decode each element.
    *. */
  def vector[A](mapper: ResultMapper[A]): ResultMapper[Vector[A]] =
    collectAs(Vector, mapper)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[Set]],
    * using the provided mapper to decode each element.
    */
  def set[A](mapper: ResultMapper[A]): ResultMapper[Set[A]] =
    collectAs(Set, mapper)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[Map]],
    * using the provided mapper to decode each key-value element.
    */
  def map[K, V](keyMapper: ResultMapper[K], valueMapper: ResultMapper[V]): ResultMapper[Map[K, V]] =
    collectAsMap(Map, keyMapper, valueMapper)

  /** Decodes a [[NeoObject]] into a [[Map]] using the provided [[KeyMapper]] & [[ResultMapper]] to decode the key-values. */
  def neoMap[K, V](keyMapper: KeyMapper[K], valueMapper: ResultMapper[V]): ResultMapper[Map[K, V]] =
    collectAsNeoMap(Map, keyMapper, valueMapper)

  /** Decodes a [[NeoObject]] into a [[Map]] using the provided [[ResultMapper]] to decode the values. */
  def neoMap[V](valueMapper: ResultMapper[V]): ResultMapper[Map[String, V]] =
    collectAsNeoMap(Map, KeyMapper.string, valueMapper)

  /** Creates a [[ResultMapper]] that will try to decode
    * the given field of an object,
    * using the provided mapper.
    *
    * @note will fail if the object doesn't have the requested field.
    *
    * @param key the name of the field to decode.
    */
  def field[A](key: String, mapper: ResultMapper[A]): ResultMapper[A] =
    neoObject.emap(_.getAs(key, mapper))

  /** Creates a [[ResultMapper]] that will try to decode
    * the given element of a list of values,
    * using the provided mapper.
    *
    * @note will fail if the index is out of bounds.
    *
    * @param idx the index of the element to decode.
    */
  def at[A](idx: Int, mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      col
        .lift(idx)
        .toRight(left = PropertyNotFoundException(key = s"index-${idx}"))
        .flatMap(mapper.decode)
    }

  /** Strategy to distinguish cases of a coproduct. */
  sealed trait CoproductDiscriminatorStrategy[S]
  object CoproductDiscriminatorStrategy {
    /** Discriminates cases based on a label of Node. */
    final case object NodeLabel extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on the type of a Relationship. */
    final case object RelationshipType extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on field of an object. */
    final case class Field[T](name: String, mapper: ResultMapper[T]) extends CoproductDiscriminatorStrategy[T]
  }

  /** Creates a [[ResultMapper]] for a coproduct.
    * Based on a given [[CoproductDiscriminatorStrategy]],
    * and the tagged [[ResultMapper]]s of each case.
    */
  def coproduct[S, A](
    strategy: CoproductDiscriminatorStrategy[S]
  ) (
    options: (S, ResultMapper[? <: A])*
  ): ResultMapper[A] = strategy match {
    case CoproductDiscriminatorStrategy.NodeLabel =>
      node.flatMap { node =>
        options.collectFirst {
          case (label, mapper) if (node.hasLabel(label)) =>
            mapper.widen[A]
        }.getOrElse(
          failed(IncoercibleException(s"Unexpected node labels: ${node.labels}"))
        )
      }

    case CoproductDiscriminatorStrategy.RelationshipType =>
      relationship.flatMap { relationship =>
        options.collectFirst {
          case (label, mapper) if (relationship.hasType(tpe = label)) =>
            mapper.widen[A]
        }.getOrElse(
          failed(IncoercibleException(s"Unexpected relationship type: ${relationship.relationshipType}"))
        )
      }

    case CoproductDiscriminatorStrategy.Field(fieldName, fieldResultMapper) =>
      field(key = fieldName, mapper = fieldResultMapper).flatMap { label =>
        options.collectFirst {
          case (`label`, mapper) =>
            mapper.widen[A]
        }.getOrElse(
          failed(IncoercibleException(s"Unexpected field label: ${label}"))
        )
      }
  }

  /** Allows deriving a [[ResultMapper]] for the product type (case class) A. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for "${P}".

Make sure ${P} is a case class composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedProductMap[P <: Product] {
    def map(obj: NeoObject): Either[ResultMapperException, P]
  }

  /** Allows deriving a [[ResultMapper]] for the coproduct type (sealed trait) A. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for "${A}".

Make sure ${A} is a sealed trait composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedCoproductInstances[A] {
    def options: List[(String, ResultMapper[? <: A])]
  }
}

private[mappers] sealed abstract class ResultMappersLowPriority0 extends ResultMappersLowPriority1 { self: ResultMapper.type =>
  /** Creates a [[ResultMapper]] that will try to decode a [[NeoObject]]
    * into a some form of [[Map]].
    *
    * @tparam K the type of the keys.
    * @tparam V the type of the values.
    * @tparam M the kind of map to create.
    * @param mapFactory the specific [[Map]] to create.
    * @param mapper the [[ResultMapper]] used to decode the values.
    * @param keyMapper the [[KeyMapper]] used to decode the keys,
    * by default uses the no-op decoder.
    */
  implicit final def collectAsNeoMap[K, V, M[_, _]](
    implicit
    mapFactory: Factory[(K, V), M[K, V]],
    keyMapper: KeyMapper[K],
    valueMapper: ResultMapper[V]
  ): ResultMapper[M[K, V]] =
    neoObject.emap { obj =>
      traverseAs(mapFactory)(obj.properties) {
        case (key, value) =>
          keyMapper.decodeKey(key) and valueMapper.decode(value)
      }
    }
}

private[mappers] sealed abstract class ResultMappersLowPriority1 extends ResultMappersLowPriority2 { self: ResultMapper.type =>
  /** Creates a [[ResultMapper]] that will collect all values
    * into the homogeneous provided collection,
    * using the provided mapper to decode each element.
    */
  implicit final def collectAs[A, C](
    implicit
    factory: Factory[A, C],
    mapper: ResultMapper[A]
  ): ResultMapper[C] =
    values.emap(col => traverseAs(factory)(col)(mapper.decode))

  /** Overload of [[collectAs]] to help implicit inference, don't call explicitly. */
  implicit final def collectAsIterable[A, C[_]](
    implicit
    factory: Factory[A, C[A]],
    mapper: ResultMapper[A]
  ): ResultMapper[C[A]] =
    collectAs(factory, mapper)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous map-like collection,
    * using the provided mapper to decode each key-value element.
    * Overload of [[collectAs]] to help implicit inference, don't call explicitly.
    */
  implicit final def collectAsMap[K, V, M[_, _]](
    implicit
    mapFactory: Factory[(K, V), M[K, V]],
    keyMapper: ResultMapper[K],
    valueMapper: ResultMapper[V]
  ): ResultMapper[M[K, V]] =
    collectAs(mapFactory, tuple(keyMapper, valueMapper))
}

private[mappers] sealed abstract class ResultMappersLowPriority2 extends ResultMappersLowPriority3 { self: ResultMapper.type =>
  /** Creates a [[ResultMapper]] that will always decode to the given singleton,
    * no matter the actual input passed to `decode`.
    */
  implicit final def singleton[S <: Singleton](
    implicit
    ev: ValueOf[S]
  ): ResultMapper[S] =
    constant(ev.value)
}

private[mappers] sealed abstract class ResultMappersLowPriority3 { self: ResultMapper.type =>
  /** Derives an opinionated [[ResultMapper]] for a given `case class`.
    * The derived mapper will attempt to decode the result as a [[NeoObject]],
    * and then decode each field using exact names matches,
    * with the mappers in the implicit scope.
    *
    * @note if you need customization of the decoding logic,
    * please refer to the [[product]] and [[productNamed]] factories.
    */
  implicit final def productDerive[P <: Product](
    implicit
    ev: DerivedProductMap[P]
  ): ResultMapper[P] =
    neoObject.emap(ev.map)

  /** Derives an opinionated [[ResultMapper]] for a given `sealed trait`.
    * The derived mapper will attempt to decode the result as a [[NeoObject]],
    * then it will discriminate the corresponding case
    * based on the `type` field and using exact names matches,
    * with the mappers in the implicit scope.
    *
    * @note if you need customization of the decoding logic,
    * please refer to the [[coproduct]] factory.
    */
  implicit final def coproductDerive[C](
    implicit
    instances: DerivedCoproductInstances[C]
  ): ResultMapper[C] =
    coproduct(
      strategy = CoproductDiscriminatorStrategy.Field(name = "type", mapper = string),
    ) (
      instances.options : _*
    )
}
