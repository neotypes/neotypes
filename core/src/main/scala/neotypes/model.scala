package neotypes
package model

import mappers.{ParameterMapper, ResultMapper}

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.{Map => JMap}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

/** Safe wrapper over a Neo4j parameter. */
final class QueryParam private[neotypes] (private[neotypes] val underlying: AnyRef) extends AnyVal
object QueryParam {
  def apply[A](scalaValue: A)(implicit mapper: ParameterMapper[A]): QueryParam =
    mapper.toQueryParam(scalaValue)

  private[neotypes] def toJavaMap(map: Map[String, QueryParam]): JMap[String, Object] =
    map.map {
      case (key, value) =>
        key -> value.underlying
    }.asJava
}

/** Data types supported by Neo4j. */
object types {
  /** Parent type of all Neo4j types. */
  sealed trait NeoType extends Product with Serializable

  /** Parent type of all Neo4j types that have named properties. */
  sealed trait NeoObject extends NeoType {
    def properties: Map[String, NeoType]

    final def get(key: String): Option[NeoType] =
      properties.get(key)

    final def getAs[T](key: String)(implicit mapper: ResultMapper[T]): Either[exceptions.ResultMapperException, T] =
      properties
        .get(key)
        .toRight(left = exceptions.PropertyNotFoundException(s"Field ${key} not found"))
        .flatMap(mapper.decode)

    final def keys: Set[String] =
      properties.keySet

    final def values: Iterable[NeoType] =
      properties.values
  }

  /** Represents a Neo4j heterogeneous list (composite type) */
  final case class NeoList(values: Iterable[NeoType]) extends NeoType

  /** Represents a Neo4j heterogeneous map (composite type) */
  final case class NeoMap(properties: Map[String, NeoType]) extends NeoObject

  /** Parent type of all Neo4j structural types. */
  sealed trait Entity extends NeoObject {
    def elementId: String

    override def properties: Map[String, Value]
  }

  /** Represents a Neo4j Node. */
  final case class Node(
      elementId: String,
      labels: Set[String],
      properties: Map[String, Value]
  ) extends Entity {
    /** Checks if this Node contains the given label; case insensitive. */
    def hasLabel(label: String): Boolean =
      labels.contains(label.trim.toLowerCase)
  }

  /** Represents a Neo4j Relationship. */
  final case class Relationship(
      elementId: String,
      relationshipType: String,
      properties: Map[String, Value],
      startNodeId: String,
      endNodeId: String
  ) extends Entity {
    /** Checks if this Relationship has the given type; case insensitive. */
    def hasType(tpe: String): Boolean =
      relationshipType == tpe.trim.toLowerCase
  }

  /** Represents a Neo4j Path. */
  sealed trait Path extends NeoType {
    def start: Node
    def end: Node

    def nodes: List[Node]
    def relationships: List[Relationship]
    def segments: List[Path.Segment]

    def contains(node: Node): Boolean
    def contains(relationship: Relationship): Boolean
  }
  object Path {
    final case class EmptyPath(node: Node) extends Path {
      override final val start: Node = node
      override final val end: Node = node
      override final val nodes: List[Node] = node :: Nil
      override final val relationships: List[Relationship] = Nil
      override final val segments: List[Segment] = Nil

      override def contains(node: Node): Boolean =
        this.node == node

      override def contains(relationship: Relationship): Boolean =
        false
    }

    final case class NonEmptyPath(segments: ::[Segment]) extends Path {
      override final val start: Node =
        segments.head.start

      override def end: Node =
        segments.last.end

      override def nodes: List[Node] =
        start :: segments.map(s => s.end)

      override def relationships: List[Relationship] =
        segments.map(s => s.relationship)

      override def contains(node: Node): Boolean =
        start == node || segments.exists(s => s.end == node)

      override def contains(relationship: Relationship): Boolean =
        segments.exists(s => s.relationship == relationship)
    }

    final case class Segment(start: Node, relationship: Relationship, end: Node)
  }

  /** Parent type of all Neo4j property types. */
  sealed trait Value extends NeoType
  object Value {
    final case class ListValue[V <: SimpleValue](values: Iterable[V]) extends Value
    sealed trait SimpleValue extends Value
    sealed trait NumberValue extends SimpleValue
    final case class Integer(value: Int) extends NumberValue
    final case class Decimal(value: Double) extends NumberValue
    final case class Str(value: String) extends SimpleValue
    final case class Bool(value: Boolean) extends SimpleValue
    final case class Bytes(value: ArraySeq[Byte]) extends SimpleValue
    final case class Point(value: NeoPoint) extends SimpleValue
    final case class Duration(value: NeoDuration) extends SimpleValue
    sealed trait TemporalInstantValue extends SimpleValue
    final case class LocalDate(value: JDate) extends TemporalInstantValue
    final case class LocalTime(value: JTime) extends TemporalInstantValue
    final case class LocalDateTime(value: JDateTime) extends TemporalInstantValue
    final case class Time(value: JZTime) extends TemporalInstantValue
    final case class DateTme(value: JZDateTime) extends TemporalInstantValue
    final case object NullValue extends SimpleValue
  }
}

/** Exceptions provided by this library. */
object exceptions {
  sealed abstract class NeotypesException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)

  final case object TransactionWasNotCreatedException extends NeotypesException(message = "Couldn't create a transaction")

  final case object CancellationException extends NeotypesException(message = "An operation was cancelled")

  sealed abstract class ResultMapperException(message: String, cause: Option[Throwable] = None) extends NeotypesException(message, cause) with NoStackTrace

  final case class PropertyNotFoundException(message: String) extends ResultMapperException(message)

  final case class IncoercibleException(message: String, cause: Option[Throwable] = None) extends ResultMapperException(message, cause)

  final case class ChainException(parts: Iterable[ResultMapperException]) extends ResultMapperException(message = "") {
    override def getMessage(): String = {
      s"Multiple decoding errors:\n${parts.view.map(ex => ex.getMessage).mkString("\n")}"
    }
  }
  object ChainException {
    def from(exceptions: ResultMapperException*): ChainException =
      new ChainException(
        parts = exceptions.view.flatMap {
          case ChainException(parts) => parts
          case decodingException => decodingException :: Nil
        }
      )
  }
}
