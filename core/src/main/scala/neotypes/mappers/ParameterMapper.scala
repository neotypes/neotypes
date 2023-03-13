package neotypes
package mappers

import model.query.QueryParam

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{Duration => JDuration, LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, Period => JPeriod, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.UUID
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

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

object ParameterMapper {
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
      QueryParam.tag[AnyRef](v)
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
      QueryParam.tag[AnyRef](f(scalaValue))
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
      QueryParam.tag[A](scalaValue)
  }

  implicit final val BooleanParameterMapper: ParameterMapper[Boolean] =
    ParameterMapper.fromCast(Boolean.box)

  implicit final val ByteArrayParameterMapper: ParameterMapper[Array[Byte]] =
    ParameterMapper.identity

  implicit final val ByteArraySeqParameterMapper: ParameterMapper[ArraySeq[Byte]] =
    ByteArrayParameterMapper.contramap(arr => arr.unsafeArray.asInstanceOf[Array[Byte]])

  implicit final val DoubleParameterMapper: ParameterMapper[Double] =
    ParameterMapper.fromCast(Double.box)

  implicit final val FloatParameterMapper: ParameterMapper[Float] =
    ParameterMapper.fromCast(Float.box)

  implicit final val IntParameterMapper: ParameterMapper[Int] =
    ParameterMapper.fromCast(Int.box)

  implicit final val LongParameterMapper: ParameterMapper[Long] =
    ParameterMapper.fromCast(Long.box)

  implicit final val StringParameterMapper: ParameterMapper[String] =
    ParameterMapper.identity

  implicit final val UUIDParameterMapper: ParameterMapper[UUID] =
    ParameterMapper[String].contramap(_.toString)

  implicit final val NeoDurationParameterMapper: ParameterMapper[NeoDuration] =
    ParameterMapper.identity

  implicit final val NeoPointParameterMapper: ParameterMapper[NeoPoint] =
    ParameterMapper.identity

  implicit final val JDurationParameterMapper: ParameterMapper[JDuration] =
    ParameterMapper.identity

  implicit final val JPeriodParameterMapper: ParameterMapper[JPeriod] =
    ParameterMapper.identity

  implicit final val JDateParameterMapper: ParameterMapper[JDate] =
    ParameterMapper.identity

  implicit final val JTimeParameterMapper: ParameterMapper[JTime] =
    ParameterMapper.identity

  implicit final val JDateTimeParameterMapper: ParameterMapper[JDateTime] =
    ParameterMapper.identity

  implicit final val JZTimeParameterMapper: ParameterMapper[JZTime] =
    ParameterMapper.identity

  implicit final val JZDateTimeParameterMapper: ParameterMapper[JZDateTime] =
    ParameterMapper.identity

  private final def iterableParameterMapper[T](mapper: ParameterMapper[T]): ParameterMapper[Iterable[T]] =
    ParameterMapper.fromCast { col =>
      col.iterator.map(v => mapper.toQueryParam(v)).asJava
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
          keyMapper.encodeKey(key) -> valueMapper.toQueryParam(v)
      }.toMap.asJava
    }

  implicit final def mapParameterMapper[K, V, M[_, _]](
    implicit keyMapper: KeyMapper[K], valueMapper: ParameterMapper[V], ev: M[K, V] <:< Iterable[(K, V)]
  ): ParameterMapper[M[K, V]] =
    iterableMapParameterMapper(keyMapper, valueMapper).contramap(ev)

  implicit final def optionAnyRefParameterMapper[T](implicit mapper: ParameterMapper[T]): ParameterMapper[Option[T]] =
    new ParameterMapper[Option[T]] {
      override def toQueryParam(optional: Option[T]): QueryParam =
        optional.fold(ifEmpty = QueryParam.NullValue)(mapper.toQueryParam)
    }
}
