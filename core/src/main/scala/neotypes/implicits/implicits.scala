package neotypes

import java.time.{LocalDate, LocalDateTime, LocalTime}

import neotypes.DeferredQueryBuilder.Part
import neotypes.exceptions.{ConversionException, PropertyNotFoundException, UncoercibleException}
import neotypes.types._
import neotypes.utils.FunctionUtils._
import neotypes.mappers.{ValueMapper, _}
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{IntegerValue, MapValue, NodeValue, RelationshipValue}
import org.neo4j.driver.v1.{Value, Session => NSession, Driver => NDriver}
import org.neo4j.driver.v1.exceptions.value.Uncoercible
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.types.{Node, Relationship, Path => NPath}
import org.neo4j.driver.v1.util.Function
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

  implicit def mapValueMapper[T: ValueMapper]: ValueMapper[Map[String, T]] = new ValueMapper[Map[String, T]] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, Map[String, T]] = {
      val mapper = implicitly[ValueMapper[T]]
      value
        .map(values => values.keys().asScala.map { key => key -> mapper.to(key, Some(values.get(key))) })
        .map(result =>
          result
            .collectFirst { case (_, l@Left(_)) => l.asInstanceOf[Either[Throwable, Map[String, T]]] }
            .getOrElse(Right(result.collect { case (k, Right(v)) => k -> v }.toMap))
        )
        .getOrElse(Right[Throwable, Map[String, T]](Map()))
    }
  }

  implicit def listValueMapper[T](implicit vm: ValueMapper[T]): ValueMapper[List[T]] = new ValueMapper[List[T]] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, List[T]] = value match {
      case Some(v) =>
        val list: List[Either[Throwable, T]] = v.asList(new Function[Value, Either[Throwable, T]] {
          override def apply(t: Value): Either[Throwable, T] = vm.to("", Some(t))
        }).asScala.toList
        list
          .collectFirst { case l@Left(ex) => l.asInstanceOf[Either[Throwable, List[T]]] }
          .getOrElse(Right(list.collect { case Right(v) => v }))
      case _ => Right(List())
    }
  }

  implicit def option[T: ValueMapper]: ValueMapper[Option[T]] = new ValueMapper[Option[T]] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, Option[T]] = value
      .map(v => implicitly[ValueMapper[T]].to(fieldName, Some(v)).right.map(Some(_)))
      .getOrElse(Right(None))
  }

  implicit def ccValueMarshallable[T](implicit resultMapper: ResultMapper[T], ct: ClassTag[T]): ValueMapper[T] = new ValueMapper[T] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] = {
      value match {
        case Some(v: MapValue) => resultMapper.to(v.keys().asScala.map(key => key -> v.get(key)).toSeq, Some(TypeHint(ct)))
        case Some(v) => resultMapper.to(Seq((fieldName, v)), Some(TypeHint(ct)))
        case None => Left(ConversionException(s"Cannot convert $fieldName [$value]"))
      }

    }
  }

  implicit def pathMarshallable[N, R](implicit nm: ResultMapper[N], rm: ResultMapper[R]): ValueMapper[Path[N, R]] = new ValueMapper[Path[N, R]] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, Path[N, R]] = value.map { v =>
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
  }

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

  implicit def optionResultMapper[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] = new ResultMapper[Option[T]] {
    override def to(fields: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Option[T]] = {
      if (fields.isEmpty) {
        Right(None)
      } else {
        mapper.to(fields, typeHint).right.map(Some(_))
      }
    }
  }

  implicit def pathRecordMarshallable[N: ResultMapper, R: ResultMapper]: ResultMapper[Path[N, R]] =
    new AbstractResultMapper[Path[N, R]]

  implicit def unitMarshallable: ResultMapper[Unit] = new ResultMapper[Unit] {
    override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Unit] = Right[Throwable, Unit](())
  }

  implicit def hlistMarshallable[H, T <: HList, LR <: HList](implicit fieldDecoder: ValueMapper[H],
                                                             tailDecoder: ResultMapper[T]): ResultMapper[H :: T] = new ResultMapper[H :: T] {
    override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, H :: T] = {
      val (headName, headValue) = value.head
      val head = fieldDecoder.to(headName, Some(headValue))
      val tail = tailDecoder.to(value.tail, None)

      head.right.flatMap(h => tail.right.map(t => h :: t))
    }
  }

  implicit def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMapper[H],
                                                                  tail: ResultMapper[T]): ResultMapper[FieldType[K, H] :: T] = new ResultMapper[FieldType[K, H] :: T] {
    override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, FieldType[K, H] :: T] = {
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
          decodedHead.right.flatMap(v => tail.to(value, typeHint).right.map(t => labelled.field[K](v) :: t))

        case _ =>
          val decodedHead = head.to(fieldName, convertedValue.find(_._1 == fieldName).map(_._2))
          decodedHead.right.flatMap(v => tail.to(convertedValue, typeHint).right.map(t => labelled.field[K](v) :: t))
      }
    }
  }

  implicit def ccMarshallable[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                             reprDecoder: Lazy[ResultMapper[R]],
                                             ct: ClassTag[A]): ResultMapper[A] = new ResultMapper[A] {
    override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = reprDecoder.value.to(value, Some(TypeHint(ct))).right.map(gen.from)
  }

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
    def asScala[F[_] : Async]: Session[F] = new Session[F](session)
  }

  implicit class DriverExt(driver: NDriver) {
    def asScala[F[_] : Async]: Driver[F] = new Driver[F](driver)
  }

  implicit class CypherString(val sc: StringContext) extends AnyVal {
    def c(args: Any*): DeferredQueryBuilder = {
      val queries = sc.parts.map(DeferredQueryBuilder.Query)
      val params = args.map(DeferredQueryBuilder.Param).iterator

      new DeferredQueryBuilder(queries.tail.foldLeft(Seq[Part](queries.head)) { (acc, q) => acc :+ params.next() :+ q })
    }
  }

}
