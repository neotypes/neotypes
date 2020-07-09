package neotypes
package implicits.mappers

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.UUID

import mappers.ParameterMapper

import org.neo4j.driver.Value
import org.neo4j.driver.types.{IsoDuration, Point}

import scala.collection.Iterable
import scala.jdk.CollectionConverters._

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

  implicit final val IsoDurationParameterMapper: ParameterMapper[IsoDuration] =
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

  implicit final val PointParameterMapper: ParameterMapper[Point] =
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

  implicit final def collectionParameterMapper[T, C[_]](implicit mapper: ParameterMapper[T], ev: C[T] <:< Iterable[T]): ParameterMapper[C[T]] =
    iterableParameterMapper(mapper).contramap(ev)

  private final def iterableMapParameterMapper[V](mapper: ParameterMapper[V]): ParameterMapper[Iterable[(String, V)]] =
    ParameterMapper.fromCast { col =>
      col.iterator.map {
        case (key, v) => key -> mapper.toQueryParam(v).underlying
      }.toMap.asJava
    }

  implicit final def mapParameterMapper[V, M[_, _]](implicit mapper: ParameterMapper[V], ev: M[String, V] <:< Iterable[(String, V)]): ParameterMapper[M[String, V]] =
    iterableMapParameterMapper(mapper).contramap(ev)

  implicit final def optionAnyRefParameterMapper[T](implicit mapper: ParameterMapper[T]): ParameterMapper[Option[T]] =
    ParameterMapper.fromCast { optional =>
      optional.map(v => mapper.toQueryParam(v).underlying).orNull
    }
}
