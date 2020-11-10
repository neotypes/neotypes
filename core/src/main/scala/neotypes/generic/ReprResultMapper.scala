package neotypes.generic

import neotypes.mappers.{ResultMapper, TypeHint, ValueMapper}

import org.neo4j.driver.Value
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.IntegerValue
import org.neo4j.driver.types.{Entity, MapAccessor => NMap}
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, Witness, labelled}

import scala.jdk.CollectionConverters._

trait ReprResultMapper[A] extends ResultMapper[A]

object ReprResultMapper {

  private def getKeyValuesFrom(nmap: NMap): Iterator[(String, Value)] =
    nmap.keys.asScala.iterator.map(key => key -> nmap.get(key))

  implicit final val HNilResultMapper: ReprResultMapper[HNil] = {
    val rm = ResultMapper.fromValueMapper[HNil](ValueMapper.HNilMapper)

    new ReprResultMapper[HNil] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, HNil] =
        rm.to(value, typeHint)
    }
  }

  implicit final def hlistMarshallable[H, T <: HList](implicit fieldDecoder: ValueMapper[H],
                                                tailDecoder: ReprResultMapper[T]): ReprResultMapper[H :: T] =
    new ReprResultMapper[H :: T] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, H :: T] = {
        val (headName, headValue) = value.head
        val head = fieldDecoder.to(headName, Some(headValue))
        val tail = tailDecoder.to(value.tail, None)

        head.flatMap(h => tail.map(t => h :: t))
      }
    }

  implicit final def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMapper[H],
                                                                  tail: ReprResultMapper[T]): ReprResultMapper[FieldType[K, H] :: T] =
    new ReprResultMapper[FieldType[K, H] :: T] {
      private def collectEntityFields(entity: Entity): List[(String, Value)] = {
        val entityId = new IntegerValue(entity.id())
        val ids = ("id" -> entityId) :: ("_id" -> entityId) :: Nil
        (getKeyValuesFrom(entity) ++ ids).toList
      }

      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, FieldType[K, H] :: T] = {
        val fieldName = key.value.name

        typeHint match {
          case Some(TypeHint(true)) =>
            val index = fieldName.substring(1).toInt - 1
            val decodedHead = head.to(fieldName, if (value.size <= index) None else Some(value(index)._2))
            decodedHead.flatMap(v => tail.to(value, typeHint).map(t => labelled.field[K](v) :: t))

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
            decodedHead.flatMap(v => tail.to(convertedValue, typeHint).map(t => labelled.field[K](v) :: t))
        }
      }
    }

}
