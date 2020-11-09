package neotypes
package implicits.mappers

import java.time._
import java.util.UUID

import mappers.{ResultMapper, TypeHint, ValueMapper}
import types.Path
import org.neo4j.driver.Value
import org.neo4j.driver.types.{IsoDuration, Node, Point, Relationship, Path => NPath}

import scala.collection.compat.Factory

trait ResultMappers extends ValueMappers {
  implicit final val BooleanResultMapper: ResultMapper[Boolean] =
    ResultMapper.fromValueMapper

  implicit final val ByteArrayResultMapper: ResultMapper[Array[Byte]] =
    ResultMapper.fromValueMapper

  implicit final val DoubleResultMapper: ResultMapper[Double] =
    ResultMapper.fromValueMapper

  implicit final val DurationTimeResultMapper: ResultMapper[Duration] =
    ResultMapper.fromValueMapper

  implicit final val FloatResultMapper: ResultMapper[Float] =
    ResultMapper.fromValueMapper

  implicit final val IntResultMapper: ResultMapper[Int] =
    ResultMapper.fromValueMapper

  implicit final val IsoDurationResultMapper: ResultMapper[IsoDuration] =
    ResultMapper.fromValueMapper

  implicit final val LocalDateResultMapper: ResultMapper[LocalDate] =
    ResultMapper.fromValueMapper

  implicit final val LocalDateTimeResultMapper: ResultMapper[LocalDateTime] =
    ResultMapper.fromValueMapper

  implicit final val LocalTimeResultMapper: ResultMapper[LocalTime] =
    ResultMapper.fromValueMapper

  implicit final val LongResultMapper: ResultMapper[Long] =
    ResultMapper.fromValueMapper

  implicit final val NodeResultMapper: ResultMapper[Node] =
    ResultMapper.fromValueMapper

  implicit final val OffsetDateTimeResultMapper: ResultMapper[OffsetDateTime] =
    ResultMapper.fromValueMapper

  implicit final val OffsetTimeResultMapper: ResultMapper[OffsetTime] =
    ResultMapper.fromValueMapper

  implicit final val PathResultMapper: ResultMapper[NPath] =
    ResultMapper.fromValueMapper

  implicit final val PeriodTimeResultMapper: ResultMapper[Period] =
    ResultMapper.fromValueMapper

  implicit final val PointResultMapper: ResultMapper[Point] =
    ResultMapper.fromValueMapper

  implicit final val RelationshipResultMapper: ResultMapper[Relationship] =
    ResultMapper.fromValueMapper

  implicit final val StringResultMapper: ResultMapper[String] =
    ResultMapper.fromValueMapper

  implicit final val UnitResultMapper: ResultMapper[Unit] =
    ResultMapper.const(())

  implicit final val UUIDResultMapper: ResultMapper[UUID] =
    ResultMapper.fromValueMapper

  implicit final val ValueResultMapper: ResultMapper[Value] =
    ResultMapper.fromValueMapper

  implicit final val ZonedDateTimeResultMapper: ResultMapper[ZonedDateTime] =
    ResultMapper.fromValueMapper

  implicit final def iterableResultMapper[T, I[_]](implicit factory: Factory[T, I[T]], mapper: ValueMapper[T]): ResultMapper[I[T]] =
    ResultMapper.fromValueMapper

  implicit final def mapResultMapper[V, M[_, _]](implicit factory: Factory[(String, V), M[String, V]], mapper: ValueMapper[V]): ResultMapper[M[String, V]] =
    ResultMapper.fromValueMapper

  implicit final def optionResultMapper[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] =
    new ResultMapper[Option[T]] {
      override def to(fields: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Option[T]] =
        fields match {
          case Nil => Right(None)
          case (_, v) :: Nil if (v.isNull) => Right(None)
          case _ => mapper.to(fields, typeHint).map(r => Option(r))
        }
    }

  implicit final def pathRecordMarshallable[N: ResultMapper, R: ResultMapper]: ResultMapper[Path[N, R]] =
    ResultMapper.fromValueMapper
}
