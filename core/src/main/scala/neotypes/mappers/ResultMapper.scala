package neotypes
package mappers

import boilerplate.BoilerplateResultMappers
import internal.syntax.either._
import internal.utils.traverseAs
import model.exceptions.{IncoercibleException, PropertyNotFoundException, ResultMapperException}
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
Could not find the ResultMapper for ${A}".

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
    emap(f andThen Right.apply)

  /** Combines the result of another mapper with this one.
    * In case both fail, the errors will be merged into a [[model.exceptions.ChainException]].
    */
  final def and[B](other: ResultMapper[B]): ResultMapper[(A, B)] =
    ResultMapper.instance { value =>
      self.decode(value) and other.decode(value)
    }

  /** Chains a fallback mapper, in case this one fails. */
  final def or[B >: A](other: ResultMapper[B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value) orElse other.decode(value)
    }

  /** Used to emulate covariance subtyping. */
  final def widen[B >: A]: ResultMapper[B] =
    self.asInstanceOf[ResultMapper[B]]
}

object ResultMapper extends BoilerplateResultMappers with ResultMappersLowPriority {
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

  /** Creates a [[ResultMapper]] that will always decode to the given singleton,
    * no matter the actual input passed to `decode`.
    */
  implicit final def singleton[S <: Singleton](implicit ev: ValueOf[S]): ResultMapper[S] =
    constant(ev.value)

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

  /** Factory to create a [[ResultMapper]] from a decoding function,}
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
    f: Value.NumberValue => Either[ResultMapperException, A]
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
    fromMatch {
      case value: NeoType =>
        value
    }

  /** [[ResultMapper]] that will decode any numeric value into an [[Int]], may lose precision. */
  implicit final val int: ResultMapper[Int] = fromNumeric {
    case Value.Integer(value) =>
      Right(value.toInt)

    case Value.Decimal(value) =>
      Right(value.toInt)
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Short]], may lose precision. */
  implicit final val short: ResultMapper[Short] = fromNumeric {
    case Value.Integer(value) =>
      Right(value.toShort)

    case Value.Decimal(value) =>
      Right(value.toShort)
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Byte]], may lose precision. */
  implicit final val byte: ResultMapper[Byte] = fromNumeric {
    case Value.Integer(value) =>
      Right(value.toByte)

    case Value.Decimal(value) =>
      Right(value.toByte)
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Long]], may lose precision. */
  implicit final val long: ResultMapper[Long] = fromNumeric {
    case Value.Integer(value) =>
      Right(value)

    case Value.Decimal(value) =>
      Right(value.toLong)
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Float]], may lose precision. */
  implicit final val float: ResultMapper[Float] = fromNumeric {
    case Value.Integer(value) =>
      Right(value.toFloat)

    case Value.Decimal(value) =>
      Right(value.toFloat)
  }

  /** [[ResultMapper]] that will decode any numeric value into an [[Double]]. */
  implicit final val double: ResultMapper[Double] = fromNumeric {
    case Value.Integer(value) =>
      Right(value.toDouble)

    case Value.Decimal(value) =>
      Right(value)
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[BigInt]]. */
  implicit final val bigInt: ResultMapper[BigInt] = fromMatch {
    case Value.Integer(value) =>
      BigInt(value)

    case Value.Str(value) =>
      BigInt(value)

    case Value.Bytes(value) =>
      BigInt(value.unsafeArray.asInstanceOf[Array[Byte]])
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[BigDecimal]]. */
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

  /** [[ResultMapper]] that will attempt to decode the input as a [[Boolean]]. */
  implicit final val boolean: ResultMapper[Boolean] = fromMatch {
    case Value.Bool(value) =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[String]]. */
  implicit final val string: ResultMapper[String] = fromMatch {
    case Value.Str(value) =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[UUID]]. */
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

  /** [[ResultMapper]] that will attempt to decode the input as a byte array. */
  implicit final val bytes: ResultMapper[ArraySeq[Byte]] = fromMatch {
    case Value.Bytes(value) =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[Node]]. */
  implicit final val node: ResultMapper[Node] = fromMatch {
    case value: Node =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[Relationship]]. */
  implicit final val relationship: ResultMapper[Relationship] = fromMatch {
    case value: Relationship =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[Path]]. */
  implicit final val path: ResultMapper[Path] = fromMatch {
    case value: Path =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[NeoPoint]]. */
  implicit final val neoPoint: ResultMapper[NeoPoint] = fromMatch {
    case Value.Point(value) =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[NeoDuration]]. */
  implicit final val neoDuration: ResultMapper[NeoDuration] = fromMatch {
    case Value.Duration(value) =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as a [[JDuration]]. */
  implicit final val javaDuration: ResultMapper[JDuration] =
    neoDuration.map(JDuration.from)

  /** [[ResultMapper]] that will attempt to decode the input as a [[JPeriod]]. */
  implicit final val javaPeriod: ResultMapper[JPeriod] =
    neoDuration.map(JPeriod.from)

  /** [[ResultMapper]] that will attempt to decode the input as a [[SDuration]]. */
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

  /** [[ResultMapper]] that will attempt to decode the input as a [[NeoObject]]. */
  implicit val neoObject: ResultMapper[NeoObject] = fromMatch {
    case value: NeoObject =>
      value
  }

  /** [[ResultMapper]] that will attempt to decode the input as an heterogeneous list of [[NeoType]] values. */
  implicit val values: ResultMapper[List[NeoType]] = fromMatch {
    case NeoList(values) =>
      values

    case value: NeoObject =>
      value.values

    case Value.ListValue(values) =>
      values
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
    * which will recover from `null` values
    * and [[PropertyNotFoundException]]s into a `None`.
    */
  implicit def option[A](implicit mapper: ResultMapper[A]): ResultMapper[Option[A]] = fromMatch {
    case Value.NullValue =>
      Right(Option.empty[A])

    case value =>
      mapper.decode(value).map(Some.apply)
  }

  /** Creates a [[ResultMapper]] that will attempt to decode the input using the two provided mappers. */
  implicit def either[A, B](implicit a: ResultMapper[A], b: ResultMapper[B]): ResultMapper[Either[A, B]] =
    a.map(Left.apply).or(b.map(Right.apply))

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[List]],
    * using the provided mapper to decode each element.
    **/
  def list[A](implicit mapper: ResultMapper[A]): ResultMapper[List[A]] =
    collectAs(mapper, List)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[Vector]],
    * using the provided mapper to decode each element.
    *. */
  def vector[A](implicit mapper: ResultMapper[A]): ResultMapper[Vector[A]] =
    collectAs(mapper, Vector)

  /** Creates a [[ResultMapper]] that will collect all values
    * into an homogeneous [[Set]],
    * using the provided mapper to decode each element.
    */
  def set[A](implicit mapper: ResultMapper[A]): ResultMapper[Set[A]] =
    collectAs(mapper, Set)

  /** Creates a [[ResultMapper]] that will try to decode
    * the given field of an object,
    * using the provided mapper.
    *
    * @note will fail if the object doesn't have the requested field.
    *
    * @param key the name of the field to decode.
    */
  def field[A](key: String)(implicit mapper: ResultMapper[A]): ResultMapper[A] =
    neoObject.emap(_.getAs(key)(mapper))

  /** Creates a [[ResultMapper]] that will try to decode
    * the given element of a list of values,
    * using the provided mapper.
    *
    * @note will fail if the index is out of bounds.
    *
    * @param idx the index of the element to decode.
    */
  def at[A](idx: Int)(implicit mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      col
        .lift(idx)
        .toRight(left = PropertyNotFoundException(key = s"index-${idx}"))
        .flatMap(mapper.decode)
    }

  /** Creates a [[ResultMapper]] that will try to decode a [[NeoObject]]
    * into a some form of [[Map]].
    *
    * @tparam K the type of the keys.
    * @tparam V the type of the values.
    * @tparam M the kind of map to create.
    * @param mapper the [[ResultMapper]] used to decode the values.
    * @param keyMapper the [[KeyMapper]] used to decode the keys,
    * by default uses the no-op decoder.
    * @param mapFactory the specific [[Map]] to create.
    */
  def neoMap[K, V, M](
    mapper: ResultMapper[V],
    keyMapper: KeyMapper[K] = KeyMapper.StringKeyMapper
  ) (
    mapFactory: Factory[(K, V), M]
  ): ResultMapper[M] =
    neoObject.emap { obj =>
      traverseAs(mapFactory)(obj.properties) {
        case (key, value) =>
          keyMapper.decodeKey(key) and mapper.decode(value)
      }
    }

  /** Decodes a [[NeoObject]] into a [[Map]] using the provided [[ResultMapper]] to decode the values. */
  implicit def map[V](implicit mapper: ResultMapper[V]): ResultMapper[Map[String, V]] =
    neoMap(mapper)(mapFactory = Map)

  /** Strategy to distinguish cases of a coproduct. */
  sealed trait CoproductDiscriminatorStrategy[S]
  object CoproductDiscriminatorStrategy {
    /** Discriminates cases based on a label of Node. */
    final case object NodeLabel extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on the type of a Relationship. */
    final case object RelationshipType extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on field of an object. */
    final case class Field[T](name: String, mapper: ResultMapper[T]) extends CoproductDiscriminatorStrategy[T]
    object Field {
      def apply[T](name: String)(implicit mapper: ResultMapper[T], ev: DummyImplicit): Field[T] =
        new Field(name, mapper)
    }
  }

  protected def coproductImpl[S, A](
    strategy: CoproductDiscriminatorStrategy[S],
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
      field(key = fieldName)(fieldResultMapper).flatMap { label =>
        options.collectFirst {
          case (`label`, mapper) =>
            mapper.widen[A]
        }.getOrElse(
          failed(IncoercibleException(s"Unexpected field label: ${label}"))
        )
      }
  }

  /** Creates a [[ResultMapper]] for a coproduct.
    * Based on a given [[CoproductDiscriminatorStrategy]],
    * and the tagged [[ResultMapper]]s of each case.
    */
  def coproduct[A]: CoproductPartiallyApplied[A] =
    new CoproductPartiallyApplied(dummy = true)

  private[neotypes] final class CoproductPartiallyApplied[T](private val dummy: Boolean) extends AnyVal {
    def apply[S](
      strategy: CoproductDiscriminatorStrategy[S]
    ) (
      options: (S, ResultMapper[? <: T])*
    ): ResultMapper[T] =
      ResultMapper.coproductImpl(strategy, options : _*)
  }

  /** Allows decoding a [[NeoType]] into a value of type [[A]]. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for ${A}".

Make sure ${A} is a case class composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedProductMap[A] {
    def map(obj: NeoObject): Either[ResultMapperException, A]
  }

  /** Allows decoding a [[NeoType]] into a value of type [[A]]. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for ${A}".

Make sure ${A} is a sealed trait composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedCoproductInstances[A] {
    def options: List[(String, ResultMapper[A])]
  }
}

sealed trait ResultMappersLowPriority { self: ResultMapper.type =>
  /** Creates a [[ResultMapper]] that will collect all values
    * into the homogeneous provided collection,
    * using the provided mapper to decode each element.
    */
  implicit def collectAs[C, A](implicit mapper: ResultMapper[A], factory: Factory[A, C]): ResultMapper[C] =
    values.emap(col => traverseAs(factory)(col)(mapper.decode))

  /** Derives an opinionated [[ResultMapper]] for a given `case class`.
    * The derived mapper will attempt to decode the result as a [[NeoObject]],
    * and then decode each field using exact names matches,
    * with the mappers in the implicit scope.
    *
    * @note if you need customization of the decoding logic,
    * please refer to the [[product]] and [[productNamed]] factories.
    */
  implicit def productDerive[A <: Product](
    implicit ev: DerivedProductMap[A]
  ): ResultMapper[A] =
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
  implicit def coproductDerive[A](
    implicit instances: DerivedCoproductInstances[A]
  ): ResultMapper[A] =
    coproductImpl(
      strategy = CoproductDiscriminatorStrategy.Field(name = "type", mapper = string),
      instances.options : _*
    )
}
