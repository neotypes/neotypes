package neotypes
package model

import mappers.{ParameterMapper, ResultMapper}

import org.neo4j.driver.types.{IsoDuration => NeoDuration, Point => NeoPoint}

import java.time.{LocalDate => JDate, LocalDateTime => JDateTime, LocalTime => JTime, OffsetTime => JZTime, ZonedDateTime => JZDateTime}
import java.util.{Map => JMap}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

/** Safe wrapper over a Neo4j query parameter. */
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
  sealed abstract class NeoType extends Product with Serializable

  /** Represents a Neo4j heterogeneous list (composite type) */
  final case class NeoList(values: List[NeoType]) extends NeoType

  /** Parent type of all Neo4j types that have named properties. */
  sealed trait NeoObject extends NeoType {
    def properties: Map[String, NeoType]

    final def get(key: String): NeoType =
      properties.getOrElse(key, default = Value.NullValue)

    final def getAs[A](key: String)(implicit mapper: ResultMapper[A]): Either[exceptions.ResultMapperException, A] =
      properties.get(key) match {
        case Some(value) =>
          mapper.decode(value)

        case None =>
          mapper.decode(Value.NullValue).left.map { ex =>
            exceptions.ChainException.from(
              exceptions = ex, exceptions.PropertyNotFoundException(key)
            )
          }
      }

    final def keys: Set[String] =
      properties.keySet

    final def values: List[NeoType] =
      properties.valuesIterator.toList
  }

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
      labels.contains(label.toLowerCase)
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
      relationshipType.equalsIgnoreCase(tpe)
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
    final case class ListValue[V <: SimpleValue](values: List[V]) extends Value
    sealed trait SimpleValue extends Value
    sealed trait NumberValue extends SimpleValue
    final case class Integer(value: Long) extends NumberValue
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
    final case class ZonedTime(value: JZTime) extends TemporalInstantValue
    final case class ZonedDateTime(value: JZDateTime) extends TemporalInstantValue
    case object NullValue extends SimpleValue
  }
}

/** Exceptions provided by this library. */
object exceptions {
  sealed abstract class NeotypesException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with NoStackTrace

  object TransactionWasNotCreatedException extends NeotypesException(
    message = "Couldn't create a transaction"
  )

  object CancellationException extends NeotypesException(
    message = "An operation was cancelled"
  )

  sealed abstract class ResultMapperException(message: String, cause: Option[Throwable] = None) extends NeotypesException(message, cause)

  object MissingRecordException extends ResultMapperException(
    message = "A record was expected but none was received"
  )

  final class KeyMapperException(key: String, cause: Throwable) extends ResultMapperException(
    message = s"Error decoding key: '${key}'",
    Some(cause)
  )
  object KeyMapperException {
    def apply(key: String, cause: Throwable): KeyMapperException =
      new KeyMapperException(key, cause)
  }

  final class PropertyNotFoundException(key: String) extends ResultMapperException(
    message = s"Field '${key}' not found"
  )
  object PropertyNotFoundException {
    def apply(key: String): PropertyNotFoundException =
      new PropertyNotFoundException(key)
  }

  final class IncoercibleException(message: String, cause: Option[Throwable]) extends ResultMapperException(message, cause)
  object IncoercibleException {
    def apply(message: String, cause: Option[Throwable] = None): IncoercibleException =
      new IncoercibleException(message, cause)
  }

  final class ChainException(private val parts: Iterable[ResultMapperException]) extends ResultMapperException(message = "") {
    override def getMessage(): String =
      parts
        .view
        .map(ex => ex.getMessage)
        .mkString(
          start = "Multiple decoding errors:" + System.lineSeparator,
          sep = System.lineSeparator,
          end = System.lineSeparator
        )
  }
  object ChainException {
    def from(exceptions: ResultMapperException*): ChainException =
      new ChainException(
        parts = exceptions.view.flatMap {
          case chainException: ChainException => chainException.parts
          case decodingException => decodingException :: Nil
        }
      )
  }
}
