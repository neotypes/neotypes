package neotypes
package mappers

import boilerplate.BoilerplateResultMappers
import model.QueryParam
import model.types._
import model.exceptions.{IncoercibleException, PropertyNotFoundException, ResultMapperException}
import internal.utils.traverseAs

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.UUID
import scala.collection.Factory
import scala.jdk.CollectionConverters._

@annotation.implicitNotFound("${A} is not a valid type for keys")
trait KeyMapper[A] { self =>
  /**
    * Encodes a value as a key.
    *
    * @param a The value to encode.
    * @tparam A The type of the value to encode.
    * @return The key corresponding to that value.
    */
  def encodeKey(a: A): String

  /**
    * Decodes a key as a value.
    *
    * @param key The key to decode.
    * @tparam A The type of the value to decode.
    * @return The value corresponding to that key or an error.
    */
  def decodeKey(key: String): Either[Throwable, A]

  /**
    * Creates a new [[KeyMapper]] by providing
    * transformation functions to and from A.
    *
    * @param f The function to apply before the encoding.
    * @param g The function to apply after the decoding.
    * @tparam B The type of the new [[KeyMapper]]
    * @return A new [[KeyMapper]] for values of type B.
    */
  final def imap[B](f: B => A)(g: A => Either[Throwable, B]): KeyMapper[B] = new KeyMapper[B] {
    override def encodeKey(b: B): String =
      self.encodeKey(f(b))

    override def decodeKey(key: String): Either[Throwable, B] =
      self.decodeKey(key).flatMap(g)
  }
}

object KeyMapper {
  /**
    * Summons an implicit [[KeyMapper]] already in scope by result type.
    *
    * @param mapper A [[KeyMapper]] in scope of the desired type.
    * @tparam A The result type of the mapper.
    * @return A [[KeyMapper]] for the given type currently in implicit scope.
    */
  def apply[A](implicit mapper: KeyMapper[A]): KeyMapper[A] = mapper

  implicit final val StringKeyMapper: KeyMapper[String] = new KeyMapper[String] {
    override def encodeKey(key: String): String =
      key

    override def decodeKey(key: String): Either[Throwable, String] =
      Right(key)
  }
}

@annotation.implicitNotFound("Could not find the ParameterMapper for ${A}")
sealed trait ParameterMapper[A] { self =>
  /**
    * Casts a Scala value of type A into a valid Neo4j parameter.
    *
    * @param scalaValue The value to cast.
    * @tparam A The type of the scalaValue.
    * @return The same value casted as a valid Neo4j parameter.
    */
  def toQueryParam(scalaValue: A): QueryParam

  /**
    * Creates a new [[ParameterMapper]] by applying a function
    * to a Scala value of type B before casting it using this mapper.
    *
    * @param f The function to apply before the cast.
    * @tparam B The input type of the supplied function.
    * @return A new [[ParameterMapper]] for values of type B.
    */
  final def contramap[B](f: B => A): ParameterMapper[B] = new ParameterMapper[B] {
    override def toQueryParam(scalaValue: B): QueryParam =
      self.toQueryParam(f(scalaValue))
  }
}

object ParameterMapper extends ParameterMappers {
  /**
    * Summons an implicit [[ParameterMapper]] already in scope by result type.
    *
    * @param mapper A [[ParameterMapper]] in scope of the desired type.
    * @tparam A The input type of the mapper.
    * @return A [[ParameterMapper]] for the given type currently in implicit scope.
    */
  def apply[A](implicit mapper: ParameterMapper[A]): ParameterMapper[A] = mapper

  /**
    * Constructs a [[ParameterMapper]] that always returns a constant value.
    *
    * @param v The value to always return.
    * @tparam A The type of the input value.
    * @return A [[ParameterMapper]] that always returns the supplied value.
    */
  def const[A](v: AnyRef): ParameterMapper[A] = new ParameterMapper[A] {
    override def toQueryParam(scalaValue: A): QueryParam =
      new QueryParam(v)
  }

  /**
    * Constructs a [[ParameterMapper]] from a cast function.
    *
    * @param f The cast function.
    * @tparam A The input type of the cast function.
    * @return a [[ParameterMapper]] that will cast its inputs using the provided function.
    */
  private[neotypes] def fromCast[A](f: A => AnyRef): ParameterMapper[A] = new ParameterMapper[A] {
    override def toQueryParam(scalaValue: A): QueryParam =
      new QueryParam(f(scalaValue))
  }

  /**
    * Constructs a [[ParameterMapper]] that works like an identity function.
    *
    * Many values do not require any mapping to be used as parameters.
    * For those cases, this private helper is useful to reduce boilerplate.
    *
    * @tparam A The type of the input value (must be a subtype of [[AnyRef]]).
    * @return A [[ParameterMapper]] that always returns its input unchanged.
    */
  private[neotypes] def identity[A <: AnyRef] = new ParameterMapper[A] {
    override def toQueryParam(scalaValue: A): QueryParam =
      new QueryParam(scalaValue)
  }
}

trait ParameterMappers {
  implicit final val BooleanParameterMapper: ParameterMapper[Boolean] =
    ParameterMapper.fromCast(Boolean.box)

  implicit final val ByteArrayParameterMapper: ParameterMapper[Array[Byte]] =
    ParameterMapper.identity

  implicit final val DoubleParameterMapper: ParameterMapper[Double] =
    ParameterMapper.fromCast(Double.box)

  implicit final val DurationParameterMapper: ParameterMapper[Duration] =
    ParameterMapper.identity

  implicit final val FloatParameterMapper: ParameterMapper[Float] =
    ParameterMapper.fromCast(Float.box)

  implicit final val IntParameterMapper: ParameterMapper[Int] =
    ParameterMapper.fromCast(Int.box)

  implicit final val IsoDurationParameterMapper: ParameterMapper[NeoDuration] =
    ParameterMapper.identity

  implicit final val LocalDateParameterMapper: ParameterMapper[LocalDate] =
    ParameterMapper.identity

  implicit final val LocalDateTimeParameterMapper: ParameterMapper[LocalDateTime] =
    ParameterMapper.identity

  implicit final val LocalTimeParameterMapper: ParameterMapper[LocalTime] =
    ParameterMapper.identity

  implicit final val LongParameterMapper: ParameterMapper[Long] =
    ParameterMapper.fromCast(Long.box)

  implicit final val OffsetDateTimeParameterMapper: ParameterMapper[OffsetDateTime] =
    ParameterMapper.identity

  implicit final val OffsetTimeParameterMapper: ParameterMapper[OffsetTime] =
    ParameterMapper.identity

  implicit final val PeriodParameterMapper: ParameterMapper[Period] =
    ParameterMapper.identity

  implicit final val PointParameterMapper: ParameterMapper[NeoPoint] =
    ParameterMapper.identity

  implicit final val StringParameterMapper: ParameterMapper[String] =
    ParameterMapper.identity

  implicit final val UUIDParameterMapper: ParameterMapper[UUID] =
    ParameterMapper[String].contramap(_.toString)

  implicit final val ValueParameterMapper: ParameterMapper[Value] =
    ParameterMapper.identity

  implicit final val ZonedDateTimeParameterMapper: ParameterMapper[ZonedDateTime] =
    ParameterMapper.identity

  private final def iterableParameterMapper[T](mapper: ParameterMapper[T]): ParameterMapper[Iterable[T]] =
    ParameterMapper.fromCast { col =>
      col.iterator.map(v => mapper.toQueryParam(v).underlying).asJava
    }

  implicit final def collectionParameterMapper[T, C[_]](
    implicit mapper: ParameterMapper[T], ev: C[T] <:< Iterable[T]
  ): ParameterMapper[C[T]] =
    iterableParameterMapper(mapper).contramap(ev)

  private final def iterableMapParameterMapper[K, V](
    keyMapper: KeyMapper[K], valueMapper: ParameterMapper[V]
  ): ParameterMapper[Iterable[(K, V)]] =
    ParameterMapper.fromCast { col =>
      col.iterator.map {
        case (key, v) =>
          keyMapper.encodeKey(key) -> valueMapper.toQueryParam(v).underlying
      }.toMap.asJava
    }

  implicit final def mapParameterMapper[K, V, M[_, _]](
    implicit keyMapper: KeyMapper[K], valueMapper: ParameterMapper[V], ev: M[K, V] <:< Iterable[(K, V)]
  ): ParameterMapper[M[K, V]] =
    iterableMapParameterMapper(keyMapper, valueMapper).contramap(ev)

  implicit final def optionAnyRefParameterMapper[T](implicit mapper: ParameterMapper[T]): ParameterMapper[Option[T]] =
    ParameterMapper.fromCast { optional =>
      optional.map(v => mapper.toQueryParam(v).underlying).orNull
    }
}

trait ResultMapper[T] {
  def decode(value: NeoType): Either[ResultMapperException, T]

  def flatMap[U](f: T => ResultMapper[U]): ResultMapper[U]

  def emap[U](f: T => Either[ResultMapperException, U]): ResultMapper[U]
  def map[U](f: T => U): ResultMapper[U]
  def widen[U >: T]: ResultMapper[U]

  def and[U](other: ResultMapper[U]): ResultMapper[(T, U)]
  def or[U >: T](other: ResultMapper[U]): ResultMapper[U]
}

object ResultMapper extends BoilerplateResultMappers with ResultMappersLowPriority {
  def apply[T](implicit mapper: ResultMapper[T]): ResultMapper[T] =
    mapper

  def constant[T](t: T): ResultMapper[T] =
    ???

  def failed[T](ex: ResultMapperException): ResultMapper[T] =
    ???

  def fromMatch[T](pf: PartialFunction[NeoType, Either[ResultMapperException, T]])(implicit ev: DummyImplicit): ResultMapper[T] =
    ???

  def fromMatch[T](pf: PartialFunction[NeoType, T]): ResultMapper[T] =
    fromMatch(pf.andThen(Right.apply _))

  def fromNumeric[T](f: Value.NumberValue => Either[ResultMapperException, T]): ResultMapper[T] = fromMatch {
    case value: Value.NumberValue =>
      f(value)
  }

  def fromTemporalInstant[T](f:Value.TemporalInstantValue => Either[ResultMapperException, T]): ResultMapper[T] = fromMatch {
    case value: Value.TemporalInstantValue =>
      f(value)
  }

  implicit final val identity: ResultMapper[NeoType] =
    fromMatch {
      case value: NeoType =>
        value
    }

  implicit final def singleton[S <: Singleton](implicit ev: ValueOf[S]): ResultMapper[S] =
    constant(ev.value)

  implicit final val int: ResultMapper[Int] = fromNumeric {
    case Value.Integer(value) =>
      Right(value)

    case Value.Decimal(value) =>
      Right(value.toInt)
  }

  implicit final val string: ResultMapper[String] = fromMatch {
    case Value.Str(value) =>
      value
  }
  // ...

  implicit final val node: ResultMapper[Node] = fromMatch {
    case value: Node =>
      value
  }

  implicit final val relationship: ResultMapper[Relationship] = fromMatch {
    case value: Relationship =>
      value
  }

  implicit final val path: ResultMapper[Path] = fromMatch {
    case value: Path =>
      value
  }

  implicit final val neoPoint: ResultMapper[NeoPoint] = fromMatch {
    case Value.Point(value) =>
      value
  }

  implicit final val neoDuration: ResultMapper[NeoDuration] = fromMatch {
    case Value.Duration(value) =>
      value
  }

  implicit val values: ResultMapper[Iterable[NeoType]] = fromMatch {
    case NeoList(values) =>
      values

    case NeoMap(values) =>
      values.values

    case entity: Entity =>
      entity.values

    case Value.ListValue(values) =>
      values
  }

  def loneElement[A](mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      col.headOption match {
        case Some(a) =>
          mapper.decode(a)

        case None =>
          Left(IncoercibleException("Values was empty"))
      }
    }

  implicit val neoObject: ResultMapper[NeoObject] = fromMatch {
    case value: NeoObject =>
      value
  }

  implicit def option[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] = fromMatch {
    case Value.NullValue =>
      Right(None)

    case value =>
      mapper.decode(value).map(Some.apply)
  }

  implicit def either[A, B](implicit a: ResultMapper[A], b: ResultMapper[B]): ResultMapper[Either[A, B]] =
    a.map(Left.apply).or(b.map(Right.apply))

  implicit def collectAs[C, T](implicit factory: Factory[T, C], mapper: ResultMapper[T]): ResultMapper[C] =
    values.emap(col => traverseAs(factory)(col.iterator)(mapper.decode))

  implicit def list[T](implicit mapper: ResultMapper[T]): ResultMapper[List[T]] =
    collectAs(List, mapper)
  // ...

  def field[T](key: String)(implicit mapper: ResultMapper[T]): ResultMapper[T] =
    neoObject.emap(_.getAs[T](key)(mapper))

  def at[T](idx: Int)(implicit mapper: ResultMapper[T]): ResultMapper[T] =
    values.emap { col =>
      val element = col match {
        case seq: collection.Seq[NeoType] => seq.lift(idx)
        case _ => col.slice(from = idx, until = idx + 1).headOption
      }

      val value = element.toRight(left = PropertyNotFoundException(key = s"index-${idx}"))
      value.flatMap(mapper.decode)
    }

  sealed trait CoproductDiscriminatorStrategy[S]
  object CoproductDiscriminatorStrategy {
    final case object NodeLabel extends CoproductDiscriminatorStrategy[String]
    final case object RelationshipType extends CoproductDiscriminatorStrategy[String]
    final case class Field[T](name: String, mapper: ResultMapper[T]) extends CoproductDiscriminatorStrategy[T]
    object Field {
      def apply[T](name: String)(implicit mapper: ResultMapper[T], ev: DummyImplicit): Field[T] =
        new Field(name, mapper)
    }
  }

  protected def coproductImpl[S, T](
    strategy: CoproductDiscriminatorStrategy[S],
    options: (S, ResultMapper[? <: T])*
  ): ResultMapper[T] = strategy match {
    case CoproductDiscriminatorStrategy.NodeLabel =>
      ResultMapper.node.flatMap { node =>
        options.collectFirst {
          case (label, mapper) if (node.hasLabel(label)) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected node labels: ${node.labels}"))
        )
      }

    case CoproductDiscriminatorStrategy.RelationshipType =>
      ResultMapper.relationship.flatMap { relationship =>
        options.collectFirst {
          case (label, mapper) if (relationship.hasType(tpe = label)) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected relationship type: ${relationship.relationshipType}"))
        )
      }

    case CoproductDiscriminatorStrategy.Field(fieldName, fieldResultMapper) =>
      ResultMapper.field(key = fieldName)(fieldResultMapper).flatMap { label =>
        options.collectFirst {
          case (`label`, mapper) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected field label: ${label}"))
        )
      }
  }

  def coproduct[T]: CoproductPartiallyApplied[T] =
    new CoproductPartiallyApplied(dummy = true)

  private[neotypes] final class CoproductPartiallyApplied[T](private val dummy: Boolean) extends AnyVal {
    def apply[S](
      strategy: CoproductDiscriminatorStrategy[S]
    ) (
      options: (S, ResultMapper[? <: T])*
    ): ResultMapper[T] =
      ResultMapper.coproductImpl(strategy, options : _*)
  }

  trait DerivedProductMap[T] {
    def map(obj: NeoObject): Either[ResultMapperException, T]
  }

  trait DerivedCoproductInstances[T] {
    def options: List[(String, ResultMapper[T])]
  }
}

sealed trait ResultMappersLowPriority { self: ResultMapper.type =>
  implicit def productDerive[T <: Product](
    implicit ev: DerivedProductMap[T]
  ): ResultMapper[T] =
    neoObject.emap(ev.map)

  implicit def coproductDerive[T](
    implicit instances: DerivedCoproductInstances[T]
  ): ResultMapper[T] =
    coproductImpl(
      strategy = CoproductDiscriminatorStrategy.Field[String](name = "type"),
      instances.options : _*
    )
}
