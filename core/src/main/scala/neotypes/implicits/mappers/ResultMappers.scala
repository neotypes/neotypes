package neotypes
package implicits.mappers

import java.time._
import java.util.UUID

import mappers.{ResultMapper, TypeHint, ValueMapper}
import exceptions.MultipleIncoercibleException
import types.Path

import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.IntegerValue
import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.types.{Entity, IsoDuration, Node, Path => NPath, Point, Relationship}
import shapeless.{HList, HNil, LabelledGeneric, Lazy, Witness, labelled, :: => :!:}
import shapeless.labelled.FieldType

import scala.collection.compat.Factory
import scala.reflect.ClassTag

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

  implicit final val HNilResultMapper: ResultMapper[HNil] =
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

  implicit final def ccMarshallable[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                             reprDecoder: Lazy[ResultMapper[R]],
                                             ct: ClassTag[A]): ResultMapper[A] =
    new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable]): Either[Throwable, A] =
        reprDecoder.value.to(value, Some(TypeHint(ct)), Nil).map(gen.from)
    }

  implicit final def hlistMarshallable[H, T <: HList, LR <: HList](implicit fieldDecoder: ValueMapper[H],
                                                             tailDecoder: ResultMapper[T]): ResultMapper[H :!: T] =
    new ResultMapper[H :!: T] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable]): Either[Throwable, H :!: T] = {
        val (headName, headValue) = value.head
        val head = fieldDecoder.to(headName, Some(headValue))
        accumulateErrorsIfExist[H, T, H](value.tail, typeHint, errors, head, identity)
      }
    }

  implicit final def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMapper[H],
                                                                  tail: ResultMapper[T]): ResultMapper[FieldType[K, H] :!: T] =
    new ResultMapper[FieldType[K, H] :!: T] {
      private def collectEntityFields(entity: Entity): List[(String, Value)] =
        (Constants.ID_FIELD_NAME -> new IntegerValue(entity.id)) :: getKeyValuesFrom(entity).toList

      override def to(value: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable]): Either[Throwable, FieldType[K, H] :!: T] = {
        val fieldName = key.value.name

        typeHint match {
          case Some(TypeHint(true)) =>
            val index = fieldName.substring(1).toInt - 1
            val decodedHead = head.to(fieldName, if (value.size <= index) None else Some(value(index)._2))
            decodedHead.flatMap(v => tail.to(value, typeHint, errors).map(t => labelled.field[K](v) :: t))

          case _ =>
            val convertedValue =
              if (value.size == 1 && value.head._2.`type` == InternalTypeSystem.TYPE_SYSTEM.NODE) {
                val node = value.head._2.asNode
                collectEntityFields(node)
              } else if (value.size == 1 && value.head._2.`type` == InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP) {
                val relationship = value.head._2.asRelationship
                collectEntityFields(relationship)
              } else if (value.size == 1 && value.head._2.`type` == InternalTypeSystem.TYPE_SYSTEM.MAP) {
                getKeyValuesFrom(value.head._2).toList
              } else {
                value
              }

            val decodedHead = head.to(fieldName, convertedValue.find(_._1 == fieldName).map(_._2))
            accumulateErrorsIfExist[H, T, FieldType[K, H]](value, typeHint, errors, decodedHead, h => labelled.field[K](h))
        }
      }
    }

  implicit final def optionResultMapper[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] =
    new ResultMapper[Option[T]] {
      override def to(fields: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable]): Either[Throwable, Option[T]] =
        fields match {
          case Nil => Right(None)
          case (_, v) :: Nil if (v.isNull) => Right(None)
          case _ => mapper.to(fields, typeHint).map(r => Option(r))
        }
    }

  implicit final def pathRecordMarshallable[N: ResultMapper, R: ResultMapper]: ResultMapper[Path[N, R]] =
    ResultMapper.fromValueMapper

  def accumulateErrorsIfExist[H, T <: HList, HR](results: List[(String, Value)], typeHint: Option[TypeHint], errors: List[Throwable], head: Either[Throwable, H], headFunc: H => HR)(implicit resultMapper: ResultMapper[T]) = {
    (resultMapper, head) match{
      case (HNilResultMapper, Left(th)) =>
        Left(MultipleIncoercibleException((th +: errors).reverse))
      case (HNilResultMapper, _) if errors.nonEmpty  =>
        Left(MultipleIncoercibleException(errors.reverse))
      case (_, Left(th)) =>
        resultMapper.to(results, typeHint, th +: errors).map(t => headFunc(null.asInstanceOf[H]) :: t)
      case (_, Right(v)) =>
        resultMapper.to(results, typeHint, errors).map(t => headFunc(v) :: t)
    }
  }
}
