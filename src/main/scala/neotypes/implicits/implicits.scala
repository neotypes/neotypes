package neotypes

import java.time.{LocalDate, LocalDateTime, LocalTime}

import neotypes.Session.LazySession
import neotypes.excpetions.{ConversionException, PropertyNotFoundException}
import neotypes.implicits.extract
import neotypes.types._
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{IntegerValue, NodeValue, RelationshipValue}
import org.neo4j.driver.v1.Value
import org.neo4j.driver.v1.types.{Node, Relationship, Path => NPath}
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness, labelled}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

package object implicits {

  def extract[T](fieldName: String, value: Option[Value], f: Value => T): Either[Throwable, T] = {
    value.map(f).map(Right(_)).getOrElse(Left(PropertyNotFoundException(s"Property $fieldName not found")))
  }

  implicit object StringValueMarshallable extends AbstractValueMarshallable[String](_.asString())

  implicit object IntValueMarshallable extends AbstractValueMarshallable[Int](_.asInt())

  implicit object LongValueMarshallable extends AbstractValueMarshallable[Long](_.asLong())

  implicit object DoubleValueMarshallable extends AbstractValueMarshallable[Double](_.asDouble())

  implicit object FloatValueMarshallable extends AbstractValueMarshallable[Float](_.asFloat())

  implicit object BooleanValueMarshallable extends AbstractValueMarshallable[Boolean](_.asBoolean())

  implicit object ByteArrayValueMarshallable extends AbstractValueMarshallable[Array[Byte]](_.asByteArray())

  implicit object LocalDateValueMarshallable extends AbstractValueMarshallable[LocalDate](_.asLocalDate())

  implicit object LocalTimeValueMarshallable extends AbstractValueMarshallable[LocalTime](_.asLocalTime())

  implicit object LocalDateTimeValueMarshallable extends AbstractValueMarshallable[LocalDateTime](_.asLocalDateTime())

  implicit object ValueValueMarshallable extends AbstractValueMarshallable[Value](identity)

  implicit object NodeValueMarshallable extends AbstractValueMarshallable[Node](_.asNode())

  implicit object PathValueMarshallable extends AbstractValueMarshallable[NPath](_.asPath())

  implicit object RelationshipValueMarshallable extends AbstractValueMarshallable[Relationship](_.asRelationship())

  implicit object HNilMarshallable extends ValueMarshallable[HNil] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, HNil] = Right(HNil)
  }

  implicit def option[T: ValueMarshallable]: ValueMarshallable[Option[T]] =
    (fieldName, value) =>
      value
        .map(v => implicitly[ValueMarshallable[T]].to(fieldName, Some(v)).map(Some(_)))
        .getOrElse(Right(None))

  implicit def ccValueMarshallable[T: RecordMarshallable]: ValueMarshallable[T] =
    (fieldName, value) => implicitly[RecordMarshallable[T]].to(Seq((fieldName, value.get)))

  implicit def pathMarshallable[N, R](implicit nm: RecordMarshallable[N], rm: RecordMarshallable[R]): ValueMarshallable[Path[N, R]] =
    (fieldName, value) =>
      value.map { v =>
        if (v.`type`() == InternalTypeSystem.TYPE_SYSTEM.PATH()) {
          val path = v.asPath()

          val nodes = path.nodes().asScala.toSeq.zipWithIndex.map {
            case (node, index) => nm.to(Seq((s"node $index", new NodeValue(node))))
          }

          val relationships = path.relationships().asScala.toSeq.zipWithIndex.map {
            case (relationship, index) => rm.to(Seq((s"relationship $index", new RelationshipValue(relationship))))
          }

          val failed = Seq(
            nodes.collectFirst { case Left(ex) => ex },
            relationships.collectFirst { case Left(ex) => ex }
          ).flatten.headOption

          failed
            .map(Left(_))
            .getOrElse(Right(new types.Path[N, R](nodes.collect { case Right(r) => r }, relationships.collect { case Right(r) => r }, path)))
        } else {
          Left(ConversionException(s"$fieldName of type ${v.`type`()} cannot be converted into Path"))
        }
      }.getOrElse(Left(PropertyNotFoundException(s"Property $fieldName not found")))

  /**
    * RecordMarshallables
    */

  implicit object StringRecordMarshallable extends AbstractRecordMarshallable[String]

  implicit object IntRecordMarshallable extends AbstractRecordMarshallable[Int]

  implicit object LongRecordMarshallable extends AbstractRecordMarshallable[Long]

  implicit object DoubleRecordMarshallable extends AbstractRecordMarshallable[Double]

  implicit object FloatRecordMarshallable extends AbstractRecordMarshallable[Float]

  implicit object BooleanRecordMarshallable extends AbstractRecordMarshallable[Boolean]

  implicit object ByteArrayRecordMarshallable extends AbstractRecordMarshallable[Array[Byte]]

  implicit object LocalDateRecordMarshallable extends AbstractRecordMarshallable[LocalDate]

  implicit object LocalTimeRecordMarshallable extends AbstractRecordMarshallable[LocalTime]

  implicit object LocalDateTimeRecordMarshallable extends AbstractRecordMarshallable[LocalDateTime]

  implicit object ValueTimeRecordMarshallable extends AbstractRecordMarshallable[Value]

  implicit object HNilRecordMarshallable extends AbstractRecordMarshallable[HNil]

  implicit object NodeRecordMarshallable extends AbstractRecordMarshallable[Node]

  implicit object RelationshipRecordMarshallable extends AbstractRecordMarshallable[Relationship]

  implicit def pathRecordMarshallable[N: RecordMarshallable, R: RecordMarshallable]: RecordMarshallable[Path[N, R]] =
    new AbstractRecordMarshallable[Path[N, R]]

  implicit def unitMarshallable: RecordMarshallable[Unit] = _ => Right[Throwable, Unit](())

  implicit def hlistMarshallable[H, T <: HList, LR <: HList](implicit fieldDecoder: ValueMarshallable[H],
                                                             tailDecoder: RecordMarshallable[T]): RecordMarshallable[H :: T] =
    (value: Seq[(String, Value)]) => {
      val (headName, headValue) = value.head
      val head = fieldDecoder.to(headName, Some(headValue))
      val tail = tailDecoder.to(value.tail)

      head.flatMap(h => tail.map(t => h :: t))
    }

  implicit def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMarshallable[H],
                                                                  tail: RecordMarshallable[T]): RecordMarshallable[FieldType[K, H] :: T] =
    (value: Seq[(String, Value)]) => {
      val fieldName = key.value.name

      val convertedValue =
        if (value.size == 1 && value.head._2.`type`() == InternalTypeSystem.TYPE_SYSTEM.NODE) {
          val node = value.head._2.asNode()
          node.keys().asScala.map(key => key -> node.get(key)).toSeq :+ (Constants.ID_FIELD_NAME, new IntegerValue(node.id()))
        } else {
          value
        }

      val decodedHead = head.to(fieldName, convertedValue.find(_._1 == fieldName).map(_._2))

      decodedHead.flatMap(v => tail.to(convertedValue).map(t => labelled.field[K](v) :: t))
    }

  implicit def ccMarshallable[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                             reprDecoder: Lazy[RecordMarshallable[R]],
                                             ct: ClassTag[A]): RecordMarshallable[A] =
    (value: Seq[(String, Value)]) => reprDecoder.value.to(value).map(gen.from)

  implicit class StringExt(query: String) {
    def query[T: RecordMarshallable](params: Map[String, AnyRef] = Map()): LazySession[T] = {
      new LazySession(query, params)
    }
  }

}

class AbstractValueMarshallable[T](f: Value => T) extends ValueMarshallable[T] {
  override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] = extract(fieldName, value, f)
}

class AbstractRecordMarshallable[T](implicit marshallable: ValueMarshallable[T]) extends RecordMarshallable[T] {
  override def to(fields: Seq[(String, Value)]): Either[Throwable, T] = {
    fields.headOption.map {
      case (name, value) => marshallable.to(name, Some(value))
    }.getOrElse(marshallable.to("", None))
  }
}