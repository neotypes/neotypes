package neotypes

import java.time.{LocalDate, LocalDateTime, LocalTime}

import neotypes.exceptions.{ConversionException, PropertyNotFoundException, UncoercibleException}
import neotypes.mappers.{ExecutionMapper, ResultMapper, TypeHint, ValueMapper}
import neotypes.types.Path
import neotypes.utils.sequence.{sequenceAsList, sequenceAsMap}
import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{IntegerValue, MapValue, NodeValue, RelationshipValue}
import org.neo4j.driver.v1.{Value, Session => NSession, Driver => NDriver}
import org.neo4j.driver.v1.exceptions.value.Uncoercible
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.types.{Node, Relationship, Path => NPath}
import shapeless.labelled.FieldType
import shapeless.{:: => :!:, HList, HNil, LabelledGeneric, Lazy, Witness, labelled}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object implicits {
  private[implicits] final class AbstractValueMapper[T](f: Value => T) extends ValueMapper[T] {
    override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] =
      extract(fieldName, value, f)
  }

  private[implicits] final class AbstractResultMapper[T](implicit marshallable: ValueMapper[T]) extends ResultMapper[T] {
    override def to(fields: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, T] =
      fields
        .headOption
        .fold(ifEmpty = marshallable.to("", None)) {
          case (name, value) => marshallable.to(name, Some(value))
        }
  }

  private[implicits] def coerce[T](value: Value, f: Value => T): Either[Throwable, T] =
    try {
      Right(f(value))
    } catch {
      case ex: Uncoercible => Left(UncoercibleException(ex.getLocalizedMessage, ex))
      case ex: Throwable   => Left(ex)
    }

  private[implicits] def extract[T](fieldName: String, value: Option[Value], f: Value => T): Either[Throwable, T] =
    value match {
      case None    => Left(PropertyNotFoundException(s"Property $fieldName not found"))
      case Some(v) => coerce(v, f)
    }

  /**
    * ValueMappers
    */

  implicit val StringValueMapper: ValueMapper[String] =
    new AbstractValueMapper[String](_.asString())

  implicit val IntValueMapper: ValueMapper[Int] =
    new AbstractValueMapper[Int](_.asInt())

  implicit val LongValueMapper: ValueMapper[Long] =
    new AbstractValueMapper[Long](_.asLong())

  implicit val DoubleValueMapper: ValueMapper[Double] =
    new AbstractValueMapper[Double](_.asDouble())

  implicit val FloatValueMapper: ValueMapper[Float] =
    new AbstractValueMapper[Float](_.asFloat())

  implicit val BooleanValueMapper: ValueMapper[Boolean] =
    new AbstractValueMapper[Boolean](_.asBoolean())

  implicit val ByteArrayValueMapper: ValueMapper[Array[Byte]] =
    new AbstractValueMapper[Array[Byte]](_.asByteArray())

  implicit val LocalDateValueMapper: ValueMapper[LocalDate] =
    new AbstractValueMapper[LocalDate](_.asLocalDate())

  implicit val LocalTimeValueMapper: ValueMapper[LocalTime] =
    new AbstractValueMapper[LocalTime](_.asLocalTime())

  implicit val LocalDateTimeValueMapper: ValueMapper[LocalDateTime] =
    new AbstractValueMapper[LocalDateTime](_.asLocalDateTime())

  implicit val ValueValueMapper: ValueMapper[Value] =
    new AbstractValueMapper[Value](identity)

  implicit val NodeValueMapper: ValueMapper[Node] =
    new AbstractValueMapper[Node](_.asNode())

  implicit val PathValueMapper: ValueMapper[NPath] =
    new AbstractValueMapper[NPath](_.asPath())

  implicit val RelationshipValueMapper: ValueMapper[Relationship] =
    new AbstractValueMapper[Relationship](_.asRelationship())

  implicit val HNilMapper: ValueMapper[HNil] =
    new ValueMapper[HNil] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, HNil] =
        Right(HNil)
    }

  implicit def mapValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[Map[String, T]] =
    new ValueMapper[Map[String, T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Map[String, T]] =
        value match {
          case None =>
            Right(Map.empty)

          case Some(value) =>
            sequenceAsMap(
              value
                .keys
                .asScala
                .iterator
                .map { key => key -> mapper.to(key, Option(value.get(key))) }
            )
        }
    }

  implicit def listValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[List[T]] =
    new ValueMapper[List[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, List[T]] =
        value match {
          case None =>
            Right(List.empty)

          case Some(value) =>
            sequenceAsList(
              value
                .values
                .asScala
                .iterator
                .map { value => mapper.to("", Option(value)) }
            )
        }
    }

  implicit def optionValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[Option[T]] =
    new ValueMapper[Option[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Option[T]] =
        value match {
          case None =>
            Right(None)

          case Some(value) =>
            mapper
              .to(fieldName, Option(value))
              .right
              .map(Option(_))
        }
    }

  implicit def ccValueMarshallable[T](implicit resultMapper: ResultMapper[T], ct: ClassTag[T]): ValueMapper[T] =
    new ValueMapper[T] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] =
        value match {
          case Some(value: MapValue) =>
            resultMapper.to(
              value
                .keys
                .asScala
                .iterator
                .map(key => key -> value.get(key))
                .toList,
              Some(TypeHint(ct))
            )

          case Some(value) =>
            resultMapper.to(Seq(fieldName -> value), Some(TypeHint(ct)))

          case None =>
            Left(ConversionException(s"Cannot convert $fieldName [$value]"))
        }
    }

  implicit def pathMarshallable[N, R](implicit nm: ResultMapper[N], rm: ResultMapper[R]): ValueMapper[Path[N, R]] =
    new ValueMapper[Path[N, R]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Path[N, R]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            if (value.`type` == InternalTypeSystem.TYPE_SYSTEM.PATH) {
              val path = value.asPath

              val nodes = path.nodes.asScala.iterator.zipWithIndex.map {
                case (node, index) => nm.to(Seq(s"node $index" -> new NodeValue(node)), None)
              }

              val relationships = path.relationships.asScala.iterator.zipWithIndex.map {
                case (relationship, index) => rm.to(Seq(s"relationship $index" -> new RelationshipValue(relationship)), None)
              }

              for {
                nodes <- sequenceAsList(nodes).right
                relationships <- sequenceAsList(relationships).right
              } yield new types.Path(nodes, relationships, path)
            } else {
              Left(ConversionException(s"$fieldName of type ${value.`type`} cannot be converted into a Path"))
            }
        }
    }

  /**
    * ResultMapper
    */

  implicit val StringResultMapper: ResultMapper[String] =
    new AbstractResultMapper[String]

  implicit val IntResultMapper: ResultMapper[Int] =
    new AbstractResultMapper[Int]

  implicit val LongResultMapper: ResultMapper[Long] =
    new AbstractResultMapper[Long]

  implicit val DoubleResultMapper: ResultMapper[Double] =
    new AbstractResultMapper[Double]

  implicit val FloatResultMapper: ResultMapper[Float] =
    new AbstractResultMapper[Float]

  implicit val BooleanResultMapper: ResultMapper[Boolean] =
    new AbstractResultMapper[Boolean]

  implicit val ByteArrayResultMapper: ResultMapper[Array[Byte]] =
    new AbstractResultMapper[Array[Byte]]

  implicit val LocalDateResultMapper: ResultMapper[LocalDate] =
    new AbstractResultMapper[LocalDate]

  implicit val LocalTimeResultMapper: ResultMapper[LocalTime] =
    new AbstractResultMapper[LocalTime]

  implicit val LocalDateTimeResultMapper: ResultMapper[LocalDateTime] =
    new AbstractResultMapper[LocalDateTime]

  implicit val ValueTimeResultMapper: ResultMapper[Value] =
    new AbstractResultMapper[Value]

  implicit val HNilResultMapper: ResultMapper[HNil] =
    new AbstractResultMapper[HNil]

  implicit val NodeResultMapper: ResultMapper[Node] =
    new AbstractResultMapper[Node]

  implicit val RelationshipResultMapper: ResultMapper[Relationship] =
    new AbstractResultMapper[Relationship]

  implicit def mapResultMapper[T: ValueMapper]: ResultMapper[Map[String, T]] =
    new AbstractResultMapper[Map[String, T]]

  implicit def optionResultMapper[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] =
    new ResultMapper[Option[T]] {
      override def to(fields: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Option[T]] =
        if (fields.isEmpty)
          Right(None)
        else
          mapper
            .to(fields, typeHint)
            .right
            .map(Option(_))
    }

  implicit def pathRecordMarshallable[N: ResultMapper, R: ResultMapper]: ResultMapper[Path[N, R]] =
    new AbstractResultMapper[Path[N, R]]

  implicit def unitMarshallable: ResultMapper[Unit] =
    new ResultMapper[Unit] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Unit] =
        Right(())
    }

  implicit def hlistMarshallable[H, T <: HList, LR <: HList](implicit fieldDecoder: ValueMapper[H],
                                                             tailDecoder: ResultMapper[T]): ResultMapper[H :!: T] =
    new ResultMapper[H :!: T] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, H :!: T] = {
        val (headName, headValue) = value.head
        val head = fieldDecoder.to(headName, Some(headValue))
        val tail = tailDecoder.to(value.tail, None)

        head.right.flatMap(h => tail.right.map(t => h :: t))
      }
    }

  implicit def keyedHconsMarshallable[K <: Symbol, H, T <: HList](implicit key: Witness.Aux[K],
                                                                  head: ValueMapper[H],
                                                                  tail: ResultMapper[T]): ResultMapper[FieldType[K, H] :!: T] =
    new ResultMapper[FieldType[K, H] :!: T] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, FieldType[K, H] :!: T] = {
        val fieldName = key.value.name

        typeHint match {
          case Some(TypeHint(true)) =>
            val index = fieldName.substring(1).toInt - 1
            val decodedHead = head.to(fieldName, if (value.size <= index) None else Some(value(index)._2))
            decodedHead.right.flatMap(v => tail.to(value, typeHint).right.map(t => labelled.field[K](v) :: t))

          case _ =>
            val convertedValue =
              if (value.size == 1 && value.head._2.`type` == InternalTypeSystem.TYPE_SYSTEM.NODE) {
                val node = value.head._2.asNode
                val nodes =
                  node
                    .keys
                    .asScala
                    .iterator
                    .map(key => key -> node.get(key))
                    .toList
                (Constants.ID_FIELD_NAME -> new IntegerValue(node.id)) :: nodes
              } else {
                value
              }

            val decodedHead = head.to(fieldName, convertedValue.find(_._1 == fieldName).map(_._2))
            decodedHead.right.flatMap(v => tail.to(convertedValue, typeHint).right.map(t => labelled.field[K](v) :: t))
        }
      }
    }

  implicit def ccMarshallable[A, R <: HList](implicit gen: LabelledGeneric.Aux[A, R],
                                             reprDecoder: Lazy[ResultMapper[R]],
                                             ct: ClassTag[A]): ResultMapper[A] =
    new ResultMapper[A] {
      override def to(value: Seq[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] =
        reprDecoder.value.to(value, Some(TypeHint(ct))).right.map(gen.from)
    }

  /**
    * ExecutionMappers
    */

  implicit val ResultSummaryExecutionMapper: ExecutionMapper[ResultSummary] =
    new ExecutionMapper[ResultSummary] {
      override def to(resultSummary: ResultSummary): Either[Throwable, ResultSummary] =
        Right(resultSummary)
    }

  implicit val UnitExecutionMapper: ExecutionMapper[Unit] =
    new ExecutionMapper[Unit] {
      override def to(resultSummary: ResultSummary): Either[Throwable, Unit] =
        Right(())
    }

  /**
    * Extras
    */

  implicit class SessionExt(val session: NSession) extends AnyVal {
    def asScala[F[_]: Async]: Session[F] =
      new Session[F](session)
  }

  implicit class DriverExt(val driver: NDriver) extends AnyVal {
    def asScala[F[_]: Async]: Driver[F] =
      new Driver[F](driver)
  }

  implicit def String2QueryBuilder(query: String): DeferredQueryBuilder =
    new DeferredQueryBuilder(List(DeferredQueryBuilder.Query(query)))

  implicit class CypherString(val sc: StringContext) extends AnyVal {
    def c(args: Any*): DeferredQueryBuilder = {
      val queries = sc.parts.iterator.map(DeferredQueryBuilder.Query)
      val params = args.iterator.map(DeferredQueryBuilder.Param)

      val parts = new Iterator[DeferredQueryBuilder.Part] {
        private var paramNext: Boolean = false
        override def hasNext: Boolean = queries.hasNext
        override def next(): DeferredQueryBuilder.Part =
          if (paramNext && params.hasNext) {
            paramNext = false
            params.next()
          } else {
            paramNext = true
            queries.next()
          }
      }

      new DeferredQueryBuilder(parts.toList)
    }
  }

  /**
    * Async
    */

  implicit class AsyncExt[F[_], T](val m: F[T]) extends AnyVal {
    def map[U](f: T => U)(implicit F: Async[F]): F[U] =
      F.map(m)(f)

    def flatMap[U](f: T => F[U])(implicit F: Async[F]): F[U] =
      F.flatMap(m)(f)

    def recoverWith[U >: T](f: PartialFunction[Throwable, F[U]])(implicit F: Async[F]): F[U] =
      F.recoverWith[T, U](m)(f)
  }

  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] =
    new Async[Future] {
      override def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
        val p = Promise[A]()
        cb {
          case Right(res) => p.complete(Success(res))
          case Left(ex)   => p.complete(Failure(ex))
        }
        p.future
      }

      override def flatMap[T, U](m: Future[T])(f: T => Future[U]): Future[U] =
        m.flatMap(f)

      override def map[T, U](m: Future[T])(f: T => U): Future[U] =
        m.map(f)

      override def recoverWith[T, U >: T](m: Future[T])(f: PartialFunction[Throwable, Future[U]]): Future[U] =
        m.recoverWith(f)

      override def failed[T](e: Throwable): Future[T] =
        Future.failed(e)

      override def success[T](t: => T): Future[T] =
        Future.successful(t)
    }
}
