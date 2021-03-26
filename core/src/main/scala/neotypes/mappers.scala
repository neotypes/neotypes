package neotypes

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.UUID

import exceptions.{ConversionException, IncoercibleException, PropertyNotFoundException}
import generic.Exported
import types.{Path, QueryParam}
import internal.utils.traverse.{traverseAs, traverseAsList}

import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{MapValue, NodeValue, RelationshipValue}
import org.neo4j.driver.Value
import org.neo4j.driver.exceptions.value.Uncoercible
import org.neo4j.driver.summary.ResultSummary
import org.neo4j.driver.types.{IsoDuration, MapAccessor => NMap, Node, Path => NPath, Point, Relationship}
import shapeless.HNil

import scala.collection.Iterable
import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

object mappers {
  @annotation.implicitNotFound("${A} is not a valid type for keys")
  trait KeyMapper[A] { self =>
    /**
      * Encodes a value as a key.
      *
      * @param a The value to encode.
      * @tparam A The type of the value to encode.
      * @return The key corresponding to that value.
      */
    def encodeKey(a: A): String

    /**
      * Decodes a key as a value.
      *
      * @param key The key to decode.
      * @tparam A The type of the value to decode.
      * @return The value corresponding to that key or an error.
      */
    def decodeKey(key: String): Either[Throwable, A]

    /**
      * Creates a new [[KeyMapper]] by providing
      * transformation functions to and from A.
      *
      * @param f The function to apply before the encoding.
      * @param g The function to apply after the decoding.
      * @tparam B The type of the new [[KeyMapper]]
      * @return A new [[KeyMapper]] for values of type B.
      */
    final def imap[B](f: B => A)(g: A => Either[Throwable, B]): KeyMapper[B] = new KeyMapper[B] {
      override def encodeKey(b: B): String =
        self.encodeKey(f(b))

      override def decodeKey(key: String): Either[Throwable, B] =
        self.decodeKey(key).flatMap(g)
    }
  }

  object KeyMapper {
    /**
      * Summons an implicit [[KeyMapper]] already in scope by result type.
      *
      * @param mapper A [[KeyMapper]] in scope of the desired type.
      * @tparam A The result type of the mapper.
      * @return A [[KeyMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: KeyMapper[A]): KeyMapper[A] = mapper

    implicit final val StringKeyMapper: KeyMapper[String] = new KeyMapper[String] {
      override def encodeKey(key: String): String =
        key

      override def decodeKey(key: String): Either[Throwable, String] =
        Right(key)
    }
  }

  @annotation.implicitNotFound("Could not find the ResultMapper for ${A}")
  trait ResultMapper[A] { self =>
    def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A]

    /**
      * Allows supplying a secondary [[neotypes.mappers.ResultMapper]] to try if the original fails.
      *
      * @param mapper A [[neotypes.mappers.ResultMapper]] to use if the current one fails.
      * @tparam AA A type that is possibly a supertype of your original [[neotypes.mappers.ResultMapper]] type.
      * @return A new [[neotypes.mappers.ResultMapper]] that returns the type of the supplied secondary mapper.
      */
    def or[AA >: A](mapper: => ResultMapper[AA]): ResultMapper[AA] = new ResultMapper[AA] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, AA] = {
        self.to(value, typeHint).left.flatMap(_ => mapper.to(value, typeHint))
      }
    }

    /**
      * Creates a new [[neotypes.mappers.ResultMapper]] by applying a function to the result value, if successful.
      *
      * @param f A function to apply to the result value of this ResultMapper.
      * @tparam B The return type of your supplied function.
      * @return A new ResultMapper that applies your function to the result.
      */
    def map[B](f: A => B): ResultMapper[B] = new ResultMapper[B] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] =
        self.to(value, typeHint).map(f)
    }

    /**
      * Bind a function over this [[neotypes.mappers.ResultMapper]], if successful.
      * Useful for creating decoders that depend on multiple values in sequence.
      *
      * @param f A function that returns a new [[neotypes.mappers.ResultMapper]].
      * @tparam B The result type of your new [[neotypes.mappers.ResultMapper]] from your function.
      * @return A new [[neotypes.mappers.ResultMapper]] derived from the value your original [[neotypes.mappers.ResultMapper]] outputs.
      */
    def flatMap[B](f: A => ResultMapper[B]): ResultMapper[B] = new ResultMapper[B] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, B] =
        self.to(value, typeHint).flatMap(a => f(a).to(value, typeHint))
    }

    /**
      * Combines the results of this [[neotypes.mappers.ResultMapper]] with another as a tuple pair.
      *
      * @param fa A second [[neotypes.mappers.ResultMapper]] that reads the same input values.
      * @tparam B The type of your second [[neotypes.mappers.ResultMapper]] results.
      * @return A [[neotypes.mappers.ResultMapper]] that produces a pair of values.
      */
    def product[B](fa: ResultMapper[B]): ResultMapper[(A, B)] = new ResultMapper[(A, B)] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, (A, B)] =
        self.flatMap(t => fa.map(a => (t, a))).to(value, typeHint)
    }

    /**
      * Produces a [[neotypes.mappers.ResultMapper]] where either the original or secondary mapper succeeds.
      * The original mapper result is on the Left side, and the secondary mapper is on the Right.
      *
      * @param fa A secondary [[neotypes.mappers.ResultMapper]] to try if the first one fails.
      * @tparam B The result type of your secondary [[neotypes.mappers.ResultMapper]].
      * @return A [[neotypes.mappers.ResultMapper]] that, if successful, will return a value of either the original or secondary type.
      */
    def either[B](fa: ResultMapper[B]): ResultMapper[Either[A, B]] = new ResultMapper[Either[A, B]] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, Either[A, B]] =
        self.to(value, typeHint) match {
          case Right(r) => Right(Left(r))
          case Left(_) => fa.to(value, typeHint) match {
            case Right(r) => Right(Right(r))
            case Left(e) => Left(e)
          }
        }
    }
  }

  object ResultMapper extends ResultMappers with ResultMappersLowPriority {
    /**
      * Summons an implicit [[neotypes.mappers.ResultMapper]] already in scope by result type.
      *
      * @param mapper A [[neotypes.mappers.ResultMapper]] in scope of the desired type.
      * @tparam A The result type of the mapper.
      * @return A [[neotypes.mappers.ResultMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: ResultMapper[A]): ResultMapper[A] = mapper

    /**
      * Constructs a [[neotypes.mappers.ResultMapper]] that always returns a constant result value.
      *
      * @param a The value to always return.
      * @tparam A The type of the result value.
      * @return A [[neotypes.mappers.ResultMapper]] that always returns the supplied value and never errors.
      */
    def const[A](a: A): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = Right(a)
    }

    /**
      * Constructs a [[neotypes.mappers.ResultMapper]] from a function that parses the results of a Neo4j query.
      *
      * The supplied function takes a sequence of String/Value pairs in the order they are returned from the query per-row.
      * It also takes a TypeHint to indicate whether or not the values are a tuple, if relevant.
      *
      * @param f A function that parses a list of returned field names/values and a supplied [[TypeHint]].
      * @tparam A The result type of this [[neotypes.mappers.ResultMapper]]
      * @return A new [[neotypes.mappers.ResultMapper]] that parses query results with the supplied function.
      */
    def instance[A](f: (List[(String, Value)], Option[TypeHint]) => Either[Throwable, A]): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = f(value, typeHint)
    }

    /**
      * Constructs a [[neotypes.mappers.ResultMapper]] that always returns the specified Throwable.
      *
      * @param failure A throwable error.
      * @tparam A The result type (never returned) of this [[neotypes.mappers.ResultMapper]]
      * @return A [[neotypes.mappers.ResultMapper]] that always returns a throwable error.
      */
    def failed[A](failure: Throwable): ResultMapper[A] = new ResultMapper[A] {
      override def to(value: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] = Left(failure)
    }

    /**
      * Constructs a [[neotypes.mappers.ResultMapper]] from a [[ValueMapper]].
      *
      * @tparam A the type of both the [[neotypes.mappers.ResultMapper]] and the [[ValueMapper]].
      * @return A [[neotypes.mappers.ResultMapper]] that delegates its behaviour to a [[ValueMapper]].
      */
    def fromValueMapper[A](implicit marshallable: ValueMapper[A]): ResultMapper[A] =
      new ResultMapper[A] {
        override def to(fields: List[(String, Value)], typeHint: Option[TypeHint]): Either[Throwable, A] =
          fields
            .headOption
            .fold(ifEmpty = marshallable.to("", None)) {
              case (name, value) => marshallable.to(name, Some(value))
            }
      }
  }

  trait ResultMappers {
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

    implicit final def iterableResultMapper[T, I[_]](
      implicit factory: Factory[T, I[T]], mapper: ValueMapper[T]
    ): ResultMapper[I[T]] =
      ResultMapper.fromValueMapper

    implicit final def mapResultMapper[K, V, M[_, _]](
      implicit factory: Factory[(K, V), M[K, V]], keyMapper: KeyMapper[K], valueMapper: ValueMapper[V]
    ): ResultMapper[M[K, V]] =
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

  trait ResultMappersLowPriority {
    implicit final def exportedResultMapper[A](implicit exported: Exported[ResultMapper[A]]): ResultMapper[A] =
      exported.instance
  }

  @annotation.implicitNotFound("Could not find the ValueMapper for ${A}")
  trait ValueMapper[A] { self =>
    def to(fieldName: String, value: Option[Value]): Either[Throwable, A]

    /**
      * Allows supplying a secondary [[ValueMapper]] to try if the original fails.
      *
      * @param mapper A [[ValueMapper]] to use if the current one fails.
      * @tparam AA A type that is possibly a supertype of your original [[ValueMapper]] type.
      * @return A new [[ValueMapper]] that returns the type of the supplied secondary mapper.
      */
    def or[AA >: A](mapper: => ValueMapper[AA]): ValueMapper[AA] = new ValueMapper[AA] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, AA] =
        self.to(fieldName, value).left.flatMap(_ => mapper.to(fieldName, value))
    }

    /**
      * Creates a new [[ValueMapper]] by applying a function to the result value, if successful.
      *
      * @param f A function to apply to the result value of this ResultMapper.
      * @tparam B The return type of your supplied function.
      * @return A new ResultMapper that applies your function to the result.
      */
    def map[B](f: A => B): ValueMapper[B] = new ValueMapper[B] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] =
        self.to(fieldName, value).map(f)
    }

    /**
      * Bind a function over this [[ValueMapper]], if successful.
      * Useful for creating decoders that depend on multiple values in sequence.
      *
      * @param f A function that returns a new [[ValueMapper]].
      * @tparam B The result type of your new [[ValueMapper]] from your function.
      * @return A new [[ValueMapper]] derived from the value your original [[ValueMapper]] outputs.
      */
    def flatMap[B](f: A => ValueMapper[B]): ValueMapper[B] = new ValueMapper[B] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, B] =
        self.to(fieldName, value).flatMap(a => f(a).to(fieldName, value))
    }

    /**
      * Combines the results of this [[ValueMapper]] with another as a tuple pair.
      *
      * @param fa A second [[ValueMapper]] that reads the same input values.
      * @tparam B The type of your second [[ValueMapper]] results.
      * @return A [[ValueMapper]] that produces a pair of values.
      */
    def product[B](fa: ValueMapper[B]): ValueMapper[(A, B)] = new ValueMapper[(A, B)] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, (A, B)] =
        self.flatMap(t => fa.map(a => (t, a))).to(fieldName, value)
    }

    /**
      * Produces a [[ValueMapper]] where either the original or secondary mapper succeeds.
      * The original mapper result is on the Left side, and the secondary mapper is on the Right.
      *
      * @param fa A secondary [[ValueMapper]] to try if the first one fails.
      * @tparam B The result type of your secondary [[ValueMapper]].
      * @return A [[ValueMapper]] that, if successful, will return a value of either the original or secondary type.
      */
    def either[B](fa: ValueMapper[B]): ValueMapper[Either[A, B]] = new ValueMapper[Either[A, B]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Either[A, B]] =
        self.to(fieldName, value) match {
          case Right(r) => Right(Left(r))
          case Left(_) => fa.to(fieldName, value) match {
            case Right(r) => Right(Right(r))
            case Left(e) => Left(e)
          }
        }
    }
  }

  object ValueMapper extends ValueMappers {
    /**
      * Summons an implicit [[ValueMapper]] already in scope by result type.
      *
      * @param mapper A [[ValueMapper]] in scope of the desired type.
      * @tparam A The result type of the mapper.
      * @return A [[ValueMapper]] for the given type currently in implicit scope.
      */
    def apply[A](implicit mapper: ValueMapper[A]): ValueMapper[A] = mapper

    /**
      * Constructs a [[ValueMapper]] that always returns a constant result value.
      *
      * @param a The value to always return.
      * @tparam A The type of the result value.
      * @return A [[ValueMapper]] that always returns the supplied value and never errors.
      */
    def const[A](a: A): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = Right(a)
    }

    /**
      * Constructs a [[ValueMapper]] from a function that parses the results of a Neo4j query.
      *
      * The supplied function takes a pair containing the field name and an optional value.
      *
      * @param f A function that parses a list of returned field names/values and a supplied [[TypeHint]].
      * @tparam A The result type of this [[ValueMapper]]
      * @return A new [[ValueMapper]] that parses query results with the supplied function.
      */
    def instance[A](f: (String, Option[Value]) => Either[Throwable, A]): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = f(fieldName, value)
    }

    /**
      * Constructs a [[ValueMapper]] that always returns the specified Throwable.
      *
      * @param failure A throwable error.
      * @tparam A The result type (never returned) of this [[ValueMapper]]
      * @return A [[ValueMapper]] that always returns a throwable error.
      */
    def failed[A](failure: Throwable): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] = Left(failure)
    }

    /**
      * Constructs a [[ValueMapper]] from a cast function.
      *
      * @param f The cast function.
      * @tparam A The output type of the cast function.
      * @return a [[ValueMapper]] that will cast its outputs using the provided function.
      */
    def fromCast[A](f: Value => A): ValueMapper[A] = new ValueMapper[A] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, A] =
        value match {
          case Some(v) =>
            Try(f(v)).toEither.left.map {
              case ex: Uncoercible => IncoercibleException(s"${ex.getLocalizedMessage} for field [${fieldName}] with value [${v}]", ex)
              case ex: Throwable => ex
            }

          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))
        }
    }
  }

  trait ValueMappers {
    implicit final val BooleanValueMapper: ValueMapper[Boolean] =
      ValueMapper.fromCast(v => v.asBoolean)

    implicit final val ByteArrayValueMapper: ValueMapper[Array[Byte]] =
      ValueMapper.fromCast(v => v.asByteArray)

    implicit final val DoubleValueMapper: ValueMapper[Double] =
      ValueMapper.fromCast(v => v.asDouble)

    implicit final val DurationValueMapper: ValueMapper[Duration] =
      ValueMapper.fromCast { v =>
        val isoDuration = v.asIsoDuration

        Duration
          .ZERO
          .plusDays(isoDuration.days)
          .plusSeconds(isoDuration.seconds)
          .plusNanos(isoDuration.nanoseconds.toLong)
      }

    implicit final val FloatValueMapper: ValueMapper[Float] =
      ValueMapper.fromCast(v => v.asFloat)

    implicit final val HNilMapper: ValueMapper[HNil] =
      ValueMapper.const(HNil)

    implicit final val IntValueMapper: ValueMapper[Int] =
      ValueMapper.fromCast(v => v.asInt)

    implicit final val IsoDurationValueMapper: ValueMapper[IsoDuration] =
      ValueMapper.fromCast(v => v.asIsoDuration)

    implicit final val LocalDateValueMapper: ValueMapper[LocalDate] =
      ValueMapper.fromCast(v => v.asLocalDate)

    implicit final val LocalDateTimeValueMapper: ValueMapper[LocalDateTime] =
      ValueMapper.fromCast(v => v.asLocalDateTime)

    implicit final val LocalTimeValueMapper: ValueMapper[LocalTime] =
      ValueMapper.fromCast(v => v.asLocalTime)

    implicit final val LongValueMapper: ValueMapper[Long] =
      ValueMapper.fromCast(v => v.asLong)

    implicit final val NodeValueMapper: ValueMapper[Node] =
      ValueMapper.fromCast(v => v.asNode)

    implicit final val OffsetDateTimeValueMapper: ValueMapper[OffsetDateTime] =
      ValueMapper.fromCast(v => v.asOffsetDateTime)

    implicit final val OffsetTimeValueMapper: ValueMapper[OffsetTime] =
      ValueMapper.fromCast(v => v.asOffsetTime)

    implicit final val PathValueMapper: ValueMapper[NPath] =
      ValueMapper.fromCast(v => v.asPath)

    implicit final val PeriodValueMapper: ValueMapper[Period] =
      ValueMapper.fromCast { v =>
        val isoDuration = v.asIsoDuration

        Period
          .ZERO
          .plusMonths(isoDuration.months)
          .plusDays(isoDuration.days)
      }

    implicit final val PointValueMapper: ValueMapper[Point] =
      ValueMapper.fromCast(v => v.asPoint)

    implicit final val RelationshipValueMapper: ValueMapper[Relationship] =
      ValueMapper.fromCast(v => v.asRelationship)

    implicit final val StringValueMapper: ValueMapper[String] =
      ValueMapper.fromCast(v => v.asString)

    implicit final val UUIDValueMapper: ValueMapper[UUID] =
      ValueMapper.fromCast(s => UUID.fromString(s.asString))

    implicit final val ValueValueMapper: ValueMapper[Value] =
      ValueMapper.fromCast(identity)

    implicit final val ZonedDateTimeValueMapper: ValueMapper[ZonedDateTime] =
      ValueMapper.fromCast(v => v.asZonedDateTime)

    implicit final def iterableValueMapper[T, I[_]](implicit factory: Factory[T, I[T]], mapper: ValueMapper[T]): ValueMapper[I[T]] =
      new ValueMapper[I[T]] {
        override def to(fieldName: String, value: Option[Value]): Either[Throwable, I[T]] =
          value match {
            case None =>
              Right(factory.newBuilder.result())

            case Some(value) =>
              traverseAs(factory)(value.values.asScala.iterator) { value =>
                mapper.to("", Option(value))
              }
          }
      }

    private def getKeyValuesFrom(nmap: NMap): Iterator[(String, Value)] =
      nmap.keys.asScala.iterator.map(key => key -> nmap.get(key))

    implicit final def mapValueMapper[K, V, M[_, _]](
      implicit factory: Factory[(K, V), M[K, V]], keyMapper: KeyMapper[K], valueMapper: ValueMapper[V]
    ): ValueMapper[M[K, V]] =
      new ValueMapper[M[K, V]] {
        override def to(fieldName: String, value: Option[Value]): Either[Throwable, M[K, V]] =
          value match {
            case None =>
              Right(factory.newBuilder.result())

            case Some(value) =>
              traverseAs(factory)(getKeyValuesFrom(value)) {
                case (key, value) =>
                  for {
                    k <- keyMapper.decodeKey(key)
                    v <- valueMapper.to(fieldName = "", value = Option(value))
                  } yield k -> v
              }
          }
      }

    implicit final def optionValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[Option[T]] =
      new ValueMapper[Option[T]] {
        override def to(fieldName: String, value: Option[Value]): Either[Throwable, Option[T]] =
          value match {
            case Some(value) if (!value.isNull) =>
              mapper.to(fieldName, Some(value)).map(r => Option(r))

            case _ =>
              Right(None)
          }
      }

    implicit final def pathMarshallable[N, R](implicit nm: ResultMapper[N], rm: ResultMapper[R]): ValueMapper[Path[N, R]] =
      new ValueMapper[Path[N, R]] {
        override def to(fieldName: String, value: Option[Value]): Either[Throwable, Path[N, R]] =
          value match {
            case None =>
              Left(PropertyNotFoundException(s"Property $fieldName not found"))

            case Some(value) =>
              if (value.`type` == InternalTypeSystem.TYPE_SYSTEM.PATH) {
                val path = value.asPath

                val nodes = traverseAsList(path.nodes.asScala.iterator.zipWithIndex) {
                  case (node, index) => nm.to((s"node $index" -> new NodeValue(node)) :: Nil, None)
                }

                val relationships = traverseAsList(path.relationships.asScala.iterator.zipWithIndex) {
                  case (relationship, index) => rm.to((s"relationship $index" -> new RelationshipValue(relationship)) :: Nil, None)
                }

                for {
                  nodes <- nodes
                  relationships <- relationships
                } yield Path(nodes, relationships, path)
              } else {
                Left(ConversionException(s"$fieldName of type ${value.`type`} cannot be converted into a Path"))
              }
          }
      }

    implicit final def ccValueMarshallable[T](implicit resultMapper: ResultMapper[T], ct: ClassTag[T]): ValueMapper[T] =
      new ValueMapper[T] {
        override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] =
          value match {
            case Some(value: MapValue) =>
              resultMapper.to(getKeyValuesFrom(value).toList, Some(TypeHint(ct)))

            case Some(value) =>
              resultMapper.to((fieldName -> value) :: Nil, Some(TypeHint(ct)))

            case None =>
              Left(ConversionException(s"Cannot convert $fieldName [$value]"))
          }
      }
  }

  @annotation.implicitNotFound("Could not find the ExecutionMapper for ${A}")
  trait ExecutionMapper[A] {
    def to(resultSummary: ResultSummary): Either[Throwable, A]
  }

  object ExecutionMapper {
    implicit final val ResultSummaryExecutionMapper: ExecutionMapper[ResultSummary] =
      new ExecutionMapper[ResultSummary] {
        override def to(resultSummary: ResultSummary): Either[Throwable, ResultSummary] =
          Right(resultSummary)
      }

    implicit final val UnitExecutionMapper: ExecutionMapper[Unit] =
      new ExecutionMapper[Unit] {
        override def to(resultSummary: ResultSummary): Either[Throwable, Unit] =
          Right(())
      }
  }

  final case class TypeHint(isTuple: Boolean)

  object TypeHint {
    def apply[A](classTag: ClassTag[A]): TypeHint =
      new TypeHint(classTag.runtimeClass.getName.startsWith("scala.Tuple"))
  }

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

  object ParameterMapper extends ParameterMappers {
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
        new QueryParam(v)
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
        new QueryParam(f(scalaValue))
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
        new QueryParam(scalaValue)
    }
  }

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
            keyMapper.encodeKey(key) -> valueMapper.toQueryParam(v).underlying
        }.toMap.asJava
      }

    implicit final def mapParameterMapper[K, V, M[_, _]](
      implicit keyMapper: KeyMapper[K], valueMapper: ParameterMapper[V], ev: M[K, V] <:< Iterable[(K, V)]
    ): ParameterMapper[M[K, V]] =
      iterableMapParameterMapper(keyMapper, valueMapper).contramap(ev)

    implicit final def optionAnyRefParameterMapper[T](implicit mapper: ParameterMapper[T]): ParameterMapper[Option[T]] =
      ParameterMapper.fromCast { optional =>
        optional.map(v => mapper.toQueryParam(v).underlying).orNull
      }
  }

}
