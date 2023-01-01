package neotypes
package mappers

import boilerplate.BoilerplateResultMappers
import model.types._
import model.exceptions.{IncoercibleException, PropertyNotFoundException, ResultMapperException}
import internal.utils.traverseAs

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{Duration => JDuration, LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, Period => JPeriod, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.UUID
import scala.collection.Factory
import scala.concurrent.duration.{FiniteDuration => SDuration}

trait ResultMapper[T] {
  def decode(value: NeoType): Either[ResultMapperException, T]

  def flatMap[U](f: T => ResultMapper[U]): ResultMapper[U]

  def emap[U](f: T => Either[ResultMapperException, U]): ResultMapper[U]
  def map[U](f: T => U): ResultMapper[U]
  def widen[U >: T]: ResultMapper[U]

  def and[U](other: ResultMapper[U]): ResultMapper[(T, U)]
  def or[U >: T](other: ResultMapper[U]): ResultMapper[U]
}

object ResultMapper extends BoilerplateResultMappers with ResultMappersLowPriority {
  def apply[T](implicit mapper: ResultMapper[T]): ResultMapper[T] =
    mapper

  def constant[T](t: T): ResultMapper[T] =
    ???

  def failed[T](ex: ResultMapperException): ResultMapper[T] =
    ???

  def fromMatch[T](pf: PartialFunction[NeoType, Either[ResultMapperException, T]])(implicit ev: DummyImplicit): ResultMapper[T] =
    ???

  def fromMatch[T](pf: PartialFunction[NeoType, T]): ResultMapper[T] =
    fromMatch(pf.andThen(Right.apply _))

  def fromNumeric[T](f: Value.NumberValue => Either[ResultMapperException, T]): ResultMapper[T] = fromMatch {
    case value: Value.NumberValue =>
      f(value)
  }

  def fromTemporalInstant[T](f:Value.TemporalInstantValue => Either[ResultMapperException, T]): ResultMapper[T] = fromMatch {
    case value: Value.TemporalInstantValue =>
      f(value)
  }

  implicit final val identity: ResultMapper[NeoType] =
    fromMatch {
      case value: NeoType =>
        value
    }

  implicit final def singleton[S <: Singleton](implicit ev: ValueOf[S]): ResultMapper[S] =
    constant(ev.value)

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

  implicit val neoObject: ResultMapper[NeoObject] = fromMatch {
    case value: NeoObject =>
      value
  }

  implicit def option[T](implicit mapper: ResultMapper[T]): ResultMapper[Option[T]] = fromMatch {
    case Value.NullValue =>
      Right(None)

    case value =>
      mapper.decode(value).map(Some.apply)
  }

  implicit def either[A, B](implicit a: ResultMapper[A], b: ResultMapper[B]): ResultMapper[Either[A, B]] =
    a.map(Left.apply).or(b.map(Right.apply))

  implicit def collectAs[C, T](implicit factory: Factory[T, C], mapper: ResultMapper[T]): ResultMapper[C] =
    values.emap(col => traverseAs(factory)(col.iterator)(mapper.decode))

  implicit def list[T](implicit mapper: ResultMapper[T]): ResultMapper[List[T]] =
    collectAs(List, mapper)
  // ...

  def field[T](key: String)(implicit mapper: ResultMapper[T]): ResultMapper[T] =
    neoObject.emap(_.getAs[T](key)(mapper))

  def at[T](idx: Int)(implicit mapper: ResultMapper[T]): ResultMapper[T] =
    values.emap { col =>
      val element = col match {
        case seq: collection.Seq[NeoType] => seq.lift(idx)
        case _ => col.slice(from = idx, until = idx + 1).headOption
      }

      val value = element.toRight(left = PropertyNotFoundException(key = s"index-${idx}"))
      value.flatMap(mapper.decode)
    }

  sealed trait CoproductDiscriminatorStrategy[S]
  object CoproductDiscriminatorStrategy {
    final case object NodeLabel extends CoproductDiscriminatorStrategy[String]
    final case object RelationshipType extends CoproductDiscriminatorStrategy[String]
    final case class Field[T](name: String, mapper: ResultMapper[T]) extends CoproductDiscriminatorStrategy[T]
    object Field {
      def apply[T](name: String)(implicit mapper: ResultMapper[T], ev: DummyImplicit): Field[T] =
        new Field(name, mapper)
    }
  }

  protected def coproductImpl[S, T](
    strategy: CoproductDiscriminatorStrategy[S],
    options: (S, ResultMapper[? <: T])*
  ): ResultMapper[T] = strategy match {
    case CoproductDiscriminatorStrategy.NodeLabel =>
      ResultMapper.node.flatMap { node =>
        options.collectFirst {
          case (label, mapper) if (node.hasLabel(label)) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected node labels: ${node.labels}"))
        )
      }

    case CoproductDiscriminatorStrategy.RelationshipType =>
      ResultMapper.relationship.flatMap { relationship =>
        options.collectFirst {
          case (label, mapper) if (relationship.hasType(tpe = label)) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected relationship type: ${relationship.relationshipType}"))
        )
      }

    case CoproductDiscriminatorStrategy.Field(fieldName, fieldResultMapper) =>
      ResultMapper.field(key = fieldName)(fieldResultMapper).flatMap { label =>
        options.collectFirst {
          case (`label`, mapper) =>
            mapper.widen[T]
        }.getOrElse(
          ResultMapper.failed(IncoercibleException(s"Unexpected field label: ${label}"))
        )
      }
  }

  def coproduct[T]: CoproductPartiallyApplied[T] =
    new CoproductPartiallyApplied(dummy = true)

  private[neotypes] final class CoproductPartiallyApplied[T](private val dummy: Boolean) extends AnyVal {
    def apply[S](
      strategy: CoproductDiscriminatorStrategy[S]
    ) (
      options: (S, ResultMapper[? <: T])*
    ): ResultMapper[T] =
      ResultMapper.coproductImpl(strategy, options : _*)
  }

  trait DerivedProductMap[T] {
    def map(obj: NeoObject): Either[ResultMapperException, T]
  }

  trait DerivedCoproductInstances[T] {
    def options: List[(String, ResultMapper[T])]
  }
}

sealed trait ResultMappersLowPriority { self: ResultMapper.type =>
  implicit def productDerive[T <: Product](
    implicit ev: DerivedProductMap[T]
  ): ResultMapper[T] =
    neoObject.emap(ev.map)

  implicit def coproductDerive[T](
    implicit instances: DerivedCoproductInstances[T]
  ): ResultMapper[T] =
    coproductImpl(
      strategy = CoproductDiscriminatorStrategy.Field[String](name = "type"),
      instances.options : _*
    )
}
