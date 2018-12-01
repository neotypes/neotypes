package neotypes

import java.time.{LocalDate, LocalDateTime, LocalTime}

import neotypes.DeferredQueryBuilder.Part
import neotypes.excpetions.{ConversionException, PropertyNotFoundException, UncoercibleException}
import neotypes.types._
import neotypes.mappers.{ValueMapper, _}
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{IntegerValue, NodeValue, RelationshipValue}
import org.neo4j.driver.v1.{Value, Session => NSession}
import org.neo4j.driver.v1.exceptions.value.Uncoercible
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.types.{Node, Relationship, Path => NPath}
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness, labelled}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

package object implicits {

  class AbstractValueMapper[T](f: Value => T) extends ValueMapper[T] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] = extract(fieldName, value, f)
  }

  class AbstractResultMapper[T](implicit marshallable: ValueMapper[T]) extends ResultMapper[T] {
    override def to(fields: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, T] = {
      fields.headOption.map {
        case (name, value) => marshallable.to(name, Some(value))
      }.getOrElse(marshallable.to("", None))
    }
  }

  private[implicits] def coerce[T](value: Value, f: Value => T): Either[Throwable, T] = {
    try {
      Right(f(value))
    } catch {
      case ex: Uncoercible => Left(UncoercibleException(ex.getLocalizedMessage, ex))
      case ex: Throwable => Left(ex)
    }
  }

  private[implicits] def extract[T](fieldName: String, value: Option[Value], f: Value => T): Either[Throwable, T] = {
    value
      .map(v => coerce(v, f))
      .getOrElse(Left(PropertyNotFoundException(s"Property $fieldName not found")))
  }

  implicit object StringValueMapper extends AbstractValueMapper[String](_.asString())

  implicit object IntValueMapper extends AbstractValueMapper[Int](_.asInt())

  implicit object LongValueMapper extends AbstractValueMapper[Long](_.asLong())

  implicit object DoubleValueMapper extends AbstractValueMapper[Double](_.asDouble())

  implicit object FloatValueMapper extends AbstractValueMapper[Float](_.asFloat())

  implicit object BooleanValueMapper extends AbstractValueMapper[Boolean](_.asBoolean())

  implicit object ByteArrayValueMapper extends AbstractValueMapper[Array[Byte]](_.asByteArray())

  implicit object LocalDateValueMapper extends AbstractValueMapper[LocalDate](_.asLocalDate())

  implicit object LocalTimeValueMapper extends AbstractValueMapper[LocalTime](_.asLocalTime())

  implicit object LocalDateTimeValueMapper extends AbstractValueMapper[LocalDateTime](_.asLocalDateTime())

  implicit object ValueValueMapper extends AbstractValueMapper[Value](identity)

  implicit object NodeValueMapper extends AbstractValueMapper[Node](_.asNode())

  implicit object PathValueMapper extends AbstractValueMapper[NPath](_.asPath())

  implicit object RelationshipValueMapper extends AbstractValueMapper[Relationship](_.asRelationship())

  implicit object HNilMapper extends ValueMapper[HNil] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, HNil] = Right(HNil)
  }

  implicit def mapValueMapper[T: ValueMapper]: ValueMapper[Map[String, T]] =
    (_: String, value: Option[Value]) => {
      value
        .map(_.asMap(v => implicitly[ValueMapper[T]].to("", Some(v))).asScala)
        .map(result =>
          result
            .collectFirst { case (_, l@Left(_)) => l.asInstanceOf[Either[Throwable, Map[String, T]]] }
            .getOrElse(Right(result.collect { case (k, Right(v)) => k -> v }.toMap))
        )
        .getOrElse(Right[Throwable, Map[String, T]](Map()))
    }

  implicit def option[T: ValueMapper]: ValueMapper[Option[T]] =
    (fieldName, value) =>
      value
        .map(v => implicitly[ValueMapper[T]].to(fieldName, Some(v)).map(Some(_)))
        .getOrElse(Right(None))

  implicit def ccValueMarshallable[T](implicit resultMapper: ResultMapper[T], ct: ClassTag[T]): ValueMapper[T] =
    (fieldName, value) => {
      implicitly[ResultMapper[T]].to(Seq((fieldName, value.get)), Some(TypeHint(ct)))
    }

  implicit def pathMarshallable[N, R](implicit nm: ResultMapper[N], rm: ResultMapper[R]): ValueMapper[Path[N, R]] =
    (fieldName, value) =>
      value.map { v =>
        if (v.`type`() == InternalTypeSystem.TYPE_SYSTEM.PATH()) {
          val path = v.asPath()

          val nodes = path.nodes().asScala.toSeq.zipWithIndex.map {
            case (node, index) => nm.to(Seq((s"node $index", new NodeValue(node))), None)
          }

          val relationships = path.relationships().asScala.toSeq.zipWithIndex.map {
            case (relationship, index) => rm.to(Seq((s"relationship $index", new RelationshipValue(relationship))), None)
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
    * ResultMapper
    */

  implicit object StringResultMapper extends AbstractResultMapper[String]

  implicit object IntResultMapper extends AbstractResultMapper[Int]

  implicit object LongResultMapper extends AbstractResultMapper[Long]

  implicit object DoubleResultMapper extends AbstractResultMapper[Double]

  implicit object FloatResultMapper extends AbstractResultMapper[Float]

  implicit object BooleanResultMapper extends AbstractResultMapper[Boolean]

  implicit object ByteArrayResultMapper extends AbstractResultMapper[Array[Byte]]

  implicit object LocalDateResultMapper extends AbstractResultMapper[LocalDate]

  implicit object LocalTimeResultMapper extends AbstractResultMapper[LocalTime]

  implicit object LocalDateTimeResultMapper extends AbstractResultMapper[LocalDateTime]

  implicit object ValueTimeResultMapper extends AbstractResultMapper[Value]

  implicit object HNilResultMapper extends AbstractResultMapper[HNil]

  implicit object NodeResultMapper extends AbstractResultMapper[Node]

  implicit object RelationshipResultMapper extends AbstractResultMapper[Relationship]

  implicit def mapResultMapper[T: ValueMapper]: ResultMapper[Map[String, T]] = new AbstractResultMapper[Map[String, T]]

  implicit def optionResultMapper[T: ValueMapper]: ResultMapper[Option[T]] = new AbstractResultMapper[Option[T]]

  implicit def pathRecordMarshallable[N: ResultMapper, R: ResultMapper]: ResultMapper[Path[N, R]] =
    new AbstractResultMapper[Path[N, R]]

  implicit def unitMarshallable: ResultMapper[Unit] = (_, _) => Right[Throwable, Unit](())

  implicit def hlistMarshallable[H, T <: HList, LR <: HList](implicit fieldDecoder: ValueMapper[H],
                                                             tailDecoder: ResultMapper[T]): ResultMapper[H :: T] =
    (value: Seq[(String, Value)], _: Option[TypeHint]) => {
      val (headName, headValue) = value.head
      val head = fieldDecoder.to(headName, Some(headValue))
      val tail = tailDecoder.to(value.tail, None)

      head.flatMap(h => tail.map(t => h :: t))
    }

  implicit def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMapper[H],
                                                                  tail: ResultMapper[T]): ResultMapper[FieldType[K, H] :: T] =
    (value: Seq[(String, Value)], typeHint: Option[TypeHint]) => {
      val fieldName = key.value.name

      val convertedValue =
        if (value.size == 1 && value.head._2.`type`() == InternalTypeSystem.TYPE_SYSTEM.NODE) {
          val node = value.head._2.asNode()
          node.keys().asScala.map(key => key -> node.get(key)).toSeq :+ (Constants.ID_FIELD_NAME, new IntegerValue(node.id()))
        } else {
          value
        }

      typeHint match {
        case Some(TypeHint(true)) =>
          val index = fieldName.substring(1).toInt - 1
          val decodedHead = head.to(fieldName, if (value.size <= index) None else Some(value(index)._2))
          decodedHead.flatMap(v => tail.to(value, typeHint).map(t => labelled.field[K](v) :: t))

        case _ =>
          val decodedHead = head.to(fieldName, convertedValue.find(_._1 == fieldName).map(_._2))
          decodedHead.flatMap(v => tail.to(convertedValue, typeHint).map(t => labelled.field[K](v) :: t))
      }
    }

  implicit def ccMarshallable[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                             reprDecoder: Lazy[ResultMapper[R]],
                                             ct: ClassTag[A]): ResultMapper[A] =
    (value: Seq[(String, Value)], _: Option[TypeHint]) => reprDecoder.value.to(value, Some(TypeHint(ct))).map(gen.from)

  /**
    * ExecutionMappers
    */

  implicit object ResultSummaryExecutionMapper extends ExecutionMapper[ResultSummary] {
    override def to(resultSummary: ResultSummary): Either[Throwable, ResultSummary] = Right(resultSummary)
  }

  implicit object UnitExecutionMapper extends ExecutionMapper[Unit] {
    override def to(resultSummary: ResultSummary): Either[Throwable, Unit] = Right(())
  }

  /**
    * Extras
    */

  implicit class StringExt(query: String) extends DeferredQueryBuilder(query)

  implicit class SessionExt(session: NSession) {
    def asScala[F[+ _] : Async]: Session[F] = new Session[F](session)
  }

  implicit class CypherString(val sc: StringContext) extends AnyVal {
    def c(args: Any*): DeferredQueryBuilder = {
      val queries = sc.parts.map(DeferredQueryBuilder.Query)
      val params = args.map(DeferredQueryBuilder.Param).iterator

      new DeferredQueryBuilder(queries.tail.foldLeft(Seq[Part](queries.head)) { (acc, q) => acc :+ params.next() :+ q })
    }
  }

}