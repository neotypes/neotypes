package neotypes
package mappers

import boilerplate.BoilerplateResultMappers
import internal.syntax.either._
import internal.utils.traverseAs
import model.types._
import model.exceptions.{IncoercibleException, PropertyNotFoundException, ResultMapperException}

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{Duration => JDuration, LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, Period => JPeriod, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.UUID
import scala.collection.Factory
import scala.concurrent.duration.{FiniteDuration => SDuration}
import scala.reflect.ClassTag

/** Allows decoding a [[NeoType]] into a value of type [[A]]. */
@annotation.implicitNotFound(
"""
Could not find the ResultMapper for ${A}".

Make sure ${A} is a supported type: https://neotypes.github.io/neotypes/types.html
If ${A} is a case class or a sealed trait,
then `import neotypes.generic.implicits._` to enable automatic derivation.
"""
)
trait ResultMapper[A] { self =>
  /** Attempts to decode the given [[NeoType]],
    * may fail with a [[ResultMapperException]].
    */
  def decode(value: NeoType): Either[ResultMapperException, A]

  /** Chains another mapper based on decoding result of this one. */
  final def flatMap[B](f: A => ResultMapper[B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).flatMap { t =>
        f(t).decode(value)
      }
    }

  /** Chains a transformation function that can fail. */
  final def emap[B](f: A => Either[ResultMapperException, B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value).flatMap(f)
    }

  /** Chains a transformation function that can not fail. */
  final def map[B](f: A => B): ResultMapper[B] =
    emap(f andThen Right.apply)

  /** Combines the result of another mapper with this one.
    * In case both fail, the errors will be merged into a [[model.exceptions.ChainException]].
    */
  final def and[B](other: ResultMapper[B]): ResultMapper[(A, B)] =
    ResultMapper.instance { value =>
      self.decode(value) and other.decode(value)
    }

  /** Chains a fallback mapper, in case this one fails. */
  final def or[B >: A](other: ResultMapper[B]): ResultMapper[B] =
    ResultMapper.instance { value =>
      self.decode(value) orElse other.decode(value)
    }

  /** Used to emulate covariance subtyping. */
  final def widen[B >: A]: ResultMapper[B] =
    self.asInstanceOf[ResultMapper[B]]
}

object ResultMapper extends BoilerplateResultMappers with ResultMappersLowPriority {
  /** Allows materializing an implicit [[ResultMapper]] as an explicit value. */
  def apply[A](implicit mapper: ResultMapper[A]): ResultMapper[A] =
    mapper

  /** Factory to create a [[ResultMapper]] from a decoding function. */
  def instance[A](f: NeoType => Either[ResultMapperException, A]): ResultMapper[A] =
    new ResultMapper[A] {
      override def decode(value: NeoType): Either[ResultMapperException, A] =
        f(value)
    }

  /** Creates a [[ResultMapper]] that will always decode to the given value,
    * no matter the actual input passed to `decode`.
    */
  def constant[A](a: A): ResultMapper[A] =
    instance(_ => Right(a))

  /** Creates a [[ResultMapper]] that will always decode to the given singleton,
    * no matter the actual input passed to `decode`.
    */
  implicit final def singleton[S <: Singleton](implicit ev: ValueOf[S]): ResultMapper[S] =
    constant(ev.value)

  /** Creates a [[ResultMapper]] that will always fail with the given [[ResultMapperException]],
    * no matter the actual input passed to `decode`.
    */
  def failed[A](ex: ResultMapperException): ResultMapper[A] =
    instance(_ => Left(ex))

  /** Factory to create a [[ResultMapper]] from a decoding function,
    * emulating pattern matching.
    */
  def fromMatch[A](
    pf: PartialFunction[NeoType, Either[ResultMapperException, A]]
  ) (
    implicit ct: ClassTag[A], ev: DummyImplicit
  ): ResultMapper[A] =
    instance(pf.orElse({
      case value =>
        Left(IncoercibleException(s"Couldn't decode ${value} into a ${ct.runtimeClass.getSimpleName}"))
    }))

  /** Factory to create a [[ResultMapper]] from a decoding function,}
    * emulating pattern matching.
    */
  def fromMatch[A](
    pf: PartialFunction[NeoType, A]
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] =
    fromMatch(pf.andThen(Right.apply _))

  /** Factory to create a [[ResultMapper]] from a decoding function over numeric values,
    * emulating pattern matching.
    */
  def fromNumeric[A](
    f: Value.NumberValue => Either[ResultMapperException, A]
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] = fromMatch {
    case value: Value.NumberValue =>
      f(value)
  }

  /** Factory to create a [[ResultMapper]] from a decoding function over temporal values,
    * emulating pattern matching.
    */
  def fromTemporalInstant[A](
    f: Value.TemporalInstantValue => Either[ResultMapperException, A]
  ) (
    implicit ct: ClassTag[A]
  ): ResultMapper[A] = fromMatch {
    case value: Value.TemporalInstantValue =>
      f(value)
  }

  /** Passthrough [[ResultMapper]], does not apply any decoding logic. */
  implicit final val identity: ResultMapper[NeoType] =
    fromMatch {
      case value: NeoType =>
        value
    }

  implicit final val int: ResultMapper[Int] = fromNumeric {
    case Value.Integer(value) =>
      Right(value)

    case Value.Decimal(value) =>
      Right(value.toInt)
  }

  implicit final val string: ResultMapper[String] = fromMatch {
    case Value.Str(value) =>
      value
  }
  // ...

  implicit final val node: ResultMapper[Node] = fromMatch {
    case value: Node =>
      value
  }

  implicit final val relationship: ResultMapper[Relationship] = fromMatch {
    case value: Relationship =>
      value
  }

  implicit final val path: ResultMapper[Path] = fromMatch {
    case value: Path =>
      value
  }

  implicit final val neoPoint: ResultMapper[NeoPoint] = fromMatch {
    case Value.Point(value) =>
      value
  }

  implicit final val neoDuration: ResultMapper[NeoDuration] = fromMatch {
    case Value.Duration(value) =>
      value
  }

  implicit final val javaDuration: ResultMapper[JDuration] =
    neoDuration.map(JDuration.from)

  implicit final val scalaDuration: ResultMapper[SDuration] =
    javaDuration.map(d => scala.concurrent.duration.Duration.fromNanos(d.toNanos))

  implicit val neoObject: ResultMapper[NeoObject] = fromMatch {
    case value: NeoObject =>
      value
  }

  implicit val values: ResultMapper[Iterable[NeoType]] = fromMatch {
    case NeoList(values) =>
      values

    case NeoMap(values) =>
      values.values

    case entity: Entity =>
      entity.values

    case Value.ListValue(values) =>
      values
  }

  def loneElement[A](mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      col.headOption match {
        case Some(a) =>
          mapper.decode(a)

        case None =>
          Left(IncoercibleException("Values was empty"))
      }
    }

  implicit def option[A](implicit mapper: ResultMapper[A]): ResultMapper[Option[A]] = fromMatch {
    case Value.NullValue =>
      Right(None)

    case value =>
      mapper.decode(value).map(Some.apply)
  }

  implicit def either[A, B](implicit a: ResultMapper[A], b: ResultMapper[B]): ResultMapper[Either[A, B]] =
    a.map(Left.apply).or(b.map(Right.apply))

  implicit def collectAs[C, A](implicit factory: Factory[A, C], mapper: ResultMapper[A]): ResultMapper[C] =
    values.emap(col => traverseAs(factory)(col.iterator)(mapper.decode))

  implicit def list[A](implicit mapper: ResultMapper[A]): ResultMapper[List[A]] =
    collectAs(List, mapper)
  // ...

  def field[A](key: String)(implicit mapper: ResultMapper[A]): ResultMapper[A] =
    neoObject.emap(_.getAs[A](key)(mapper))

  def at[A](idx: Int)(implicit mapper: ResultMapper[A]): ResultMapper[A] =
    values.emap { col =>
      val element = col match {
        case seq: collection.Seq[NeoType] => seq.lift(idx)
        case _ => col.slice(from = idx, until = idx + 1).headOption
      }

      val value = element.toRight(left = PropertyNotFoundException(key = s"index-${idx}"))
      value.flatMap(mapper.decode)
    }

  /** Strategy to distinguish cases of a coproduct. */
  sealed trait CoproductDiscriminatorStrategy[S]
  object CoproductDiscriminatorStrategy {
    /** Discriminates cases based on a label of Node. */
    final case object NodeLabel extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on the type of a Relationship. */
    final case object RelationshipType extends CoproductDiscriminatorStrategy[String]

    /** Discriminates cases based on field of an object. */
    final case class Field[T](name: String, mapper: ResultMapper[T]) extends CoproductDiscriminatorStrategy[T]
    object Field {
      def apply[T](name: String)(implicit mapper: ResultMapper[T], ev: DummyImplicit): Field[T] =
        new Field(name, mapper)
    }
  }

  protected def coproductImpl[S, A](
    strategy: CoproductDiscriminatorStrategy[S],
    options: (S, ResultMapper[? <: A])*
  ): ResultMapper[A] = strategy match {
    case CoproductDiscriminatorStrategy.NodeLabel =>
      ResultMapper.node.flatMap { node =>
        options.collectFirst {
          case (label, mapper) if (node.hasLabel(label)) =>
            mapper.widen[A]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected node labels: ${node.labels}"))
        )
      }

    case CoproductDiscriminatorStrategy.RelationshipType =>
      ResultMapper.relationship.flatMap { relationship =>
        options.collectFirst {
          case (label, mapper) if (relationship.hasType(tpe = label)) =>
            mapper.widen[A]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected relationship type: ${relationship.relationshipType}"))
        )
      }

    case CoproductDiscriminatorStrategy.Field(fieldName, fieldResultMapper) =>
      ResultMapper.field(key = fieldName)(fieldResultMapper).flatMap { label =>
        options.collectFirst {
          case (`label`, mapper) =>
            mapper.widen[A]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected field label: ${label}"))
        )
      }
  }

  /** Creates a [[ResultMapper]] for a coproduct.
    * Based on a given [[CoproductDiscriminatorStrategy]],
    * and the tagged [[ResultMapper]]s of each case.
    */
  def coproduct[A]: CoproductPartiallyApplied[A] =
    new CoproductPartiallyApplied(dummy = true)

  private[neotypes] final class CoproductPartiallyApplied[T](private val dummy: Boolean) extends AnyVal {
    def apply[S](
      strategy: CoproductDiscriminatorStrategy[S]
    ) (
      options: (S, ResultMapper[? <: T])*
    ): ResultMapper[T] =
      ResultMapper.coproductImpl(strategy, options : _*)
  }

  /** Allows decoding a [[NeoType]] into a value of type [[A]]. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for ${A}".

Make sure ${A} is a case class composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedProductMap[A] {
    def map(obj: NeoObject): Either[ResultMapperException, A]
  }

  /** Allows decoding a [[NeoType]] into a value of type [[A]]. */
  @annotation.implicitNotFound(
"""
Could not derive a ResultMapper for ${A}".

Make sure ${A} is a sealed trait composed of supported types: https://neotypes.github.io/neotypes/types.html,
and that you imported `neotypes.generic.implicits._`
"""
  )
  trait DerivedCoproductInstances[A] {
    def options: List[(String, ResultMapper[A])]
  }
}

sealed trait ResultMappersLowPriority { self: ResultMapper.type =>
  /** Derives an opinionated [[ResultMapper]] for a given `case class`.
    * The derived mapper will attempt to decode the result as a [[NeoObject]],
    * and then decode each field using exact names matches,
    * with the mappers in the implicit scope.
    *
    * @note if you need customization of the decoding logic,
    * please refer to the [[product]] and [[productNamed]] factories.
    */
  implicit def productDerive[A <: Product](
    implicit ev: DerivedProductMap[A]
  ): ResultMapper[A] =
    neoObject.emap(ev.map)

  /** Derives an opinionated [[ResultMapper]] for a given `sealed trait`.
    * The derived mapper will attempt to decode the result as a [[NeoObject]],
    * then it will discriminate the corresponding case
    * based on the `type` field and using exact names matches,
    * with the mappers in the implicit scope.
    *
    * @note if you need customization of the decoding logic,
    * please refer to the [[coproduct]] factory.
    */
  implicit def coproductDerive[A](
    implicit instances: DerivedCoproductInstances[A]
  ): ResultMapper[A] =
    coproductImpl(
      strategy = CoproductDiscriminatorStrategy.Field[String](name = "type"),
      instances.options : _*
    )
}
