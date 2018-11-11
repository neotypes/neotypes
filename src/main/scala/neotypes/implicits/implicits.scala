package neotypes

import neotypes.excpetions.PropertyNotFoundException
import org.neo4j.driver.internal.value.IntegerValue
import org.neo4j.driver.v1.Value
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness, labelled}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

package object implicits {

  def extract[T](fieldName: String, value: Option[Value], f: Value => T): Either[Throwable, T] = {
    value.map(f).map(Right(_)).getOrElse(Left(PropertyNotFoundException(s"Property $fieldName not found")))
  }

  implicit object StringValueMarshallable extends ValueMarshallable[String] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, String] = extract(fieldName, value, _.asString())
  }

  implicit object IntValueMarshallable extends ValueMarshallable[Int] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, Int] = extract(fieldName, value, _.asInt())
  }

  implicit object LongValueMarshallable extends ValueMarshallable[Long] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, Long] = extract(fieldName, value, _.asLong())
  }

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

  /**
    * RecordMarshallables
    */

  implicit def genericMarshallable[T](implicit marshallable: ValueMarshallable[T]): RecordMarshallable[T] =
    records => {
      records.headOption.map { case (name, value) =>
        marshallable.to(name, Some(value))
      }.getOrElse(marshallable.to("", None))
    }

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
        if (value.size == 1 && value.head._2.`type`().name() == "NODE") {
          val node = value.head._2.asNode()
          node.keys().asScala.map(key => key -> node.get(key)).toSeq :+ ("id", new IntegerValue(node.id()))
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
}
