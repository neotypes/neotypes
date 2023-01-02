package neotypes

import internal.syntax.either._
import internal.utils.traverseAs
import mappers.ResultMapper
import model.exceptions.{ResultMapperException, IncoercibleException}
import model.types._

import org.neo4j.driver.{Record => NeoRecord, Value => NeoValue}
import org.neo4j.driver.types.{MapAccessor => NeoEntity, Node => NeoNode, Path => NeoPath, Relationship => NeoRelationship, TypeSystem}

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._
import scala.util.Try

object Parser {
  val Types = TypeSystem.getDefault

  /** Attempts to parse the properties of a [[NeoEntity]]. */
  private def parseProperties[V <: NeoType](
    entity: NeoEntity
  ) (
    parseValue: NeoValue => Either[ResultMapperException, V]
  ): Either[ResultMapperException, Map[String, V]] =
    traverseAs(Map.mapFactory[String, V])(entity.keys.asScala.iterator) { key =>
      parseValue(entity.get(key)).map { value =>
        key -> value
      }
    }

  /** Parses and decodes a [[NeoRecord]] into a value using a [[ResultMapper]]. */
  def decodeRecord[A](record: NeoRecord, mapper: ResultMapper[A]): Either[ResultMapperException, A] =
    parseRecord(record).flatMap(mapper.decode)

  /** Parses a Neo4j [[NeoRecord]] into a [[NeoMap]]. */
  def parseRecord(record: NeoRecord): Either[ResultMapperException, NeoMap] =
    parseProperties(entity = record)(parseNeoType).map(NeoMap.apply)

  /** Parses a Neo4j [[NeoValue]] into a [[NeoType]]. */
  def parseNeoType(value: NeoValue): Either[ResultMapperException, NeoType] =
    if (value.hasType(Types.NODE))
      parseNeoNode(value.asNode)
    else if (value.hasType(Types.RELATIONSHIP))
      parseNeoRelationship(value.asRelationship)
    else if (value.hasType(Types.PATH))
      parseNeoPath(value.asPath)
    else if (value.hasType(Types.MAP))
      parseProperties(entity = value)(parseNeoType).map(NeoMap.apply)
    else if (value.hasType(Types.LIST))
      traverseAs(List.iterableFactory[NeoType])(value.values.asScala.iterator)(parseNeoType).map(NeoList.apply)
    else
      parseNeoValue(value)

  /** Parses a Neo4j [[NeoValue]] into a [[Value]]. */
  def parseNeoValue(value: NeoValue): Either[ResultMapperException, Value] = {
    def parseSimpleValue(value: NeoValue):  Either[ResultMapperException, Value.SimpleValue] =
      if (value.hasType(Types.INTEGER))
        Right(Value.Integer(value.asLong))
      else if (value.hasType(Types.FLOAT))
        Right(Value.Decimal(value.asDouble))
      else if (value.hasType(Types.STRING))
        Right(Value.Str(value.asString))
      else if (value.hasType(Types.BOOLEAN))
        Right(Value.Bool(value.asBoolean))
      else if (value.hasType(Types.BYTES))
        Right(Value.Bytes(ArraySeq.unsafeWrapArray(value.asByteArray)))
      else if (value.hasType(Types.POINT))
        Right(Value.Point(value.asPoint))
      else if (value.hasType(Types.DURATION))
        Right(Value.Duration(value.asIsoDuration))
      else if (value.hasType(Types.DATE))
        parseNeoTemporalValue(Value.LocalDate(value.asLocalDate))
      else if (value.hasType(Types.LOCAL_TIME))
        parseNeoTemporalValue(Value.LocalTime(value.asLocalTime))
      else if (value.hasType(Types.LOCAL_DATE_TIME))
        parseNeoTemporalValue(Value.LocalDateTime(value.asLocalDateTime))
      else if (value.hasType(Types.TIME))
        parseNeoTemporalValue(Value.ZonedTime(value.asOffsetTime))
      else if (value.hasType(Types.DATE_TIME))
        parseNeoTemporalValue(Value.ZonedDateTime(value.asZonedDateTime))
      else if (value.hasType(Types.NULL))
        Right(Value.NullValue)
      else
        Left(IncoercibleException(
          message = s"Unknown type '${value.`type`}' for value: ${value}"
        ))

    if (value.hasType(Types.LIST))
      traverseAs(List.iterableFactory[Value.SimpleValue])(value.values.asScala.iterator)(parseSimpleValue).map(Value.ListValue.apply)
    else
      parseSimpleValue(value)
  }

  /** Attempts to parse a [[NeoNode]] as a [[Node]] */
  private def parseNeoNode(node: NeoNode): Either[ResultMapperException, Node] =
    parseProperties(entity = node)(parseNeoValue).map { properties =>
      Node(
        elementId = node.elementId,
        labels = node.labels.asScala.iterator.map(_.trim.toLowerCase).toSet,
        properties
      )
    }

  /** Attempts to parse a [[NeoRelationship]] as a [[Relationship]] */
  private def parseNeoRelationship(relationship: NeoRelationship): Either[ResultMapperException, Relationship] =
    parseProperties(entity = relationship)(parseNeoValue).map { properties =>
      Relationship(
        elementId = relationship.elementId,
        relationshipType = relationship.`type`.trim.toLowerCase,
        properties,
        startNodeId = relationship.startNodeElementId,
        endNodeId = relationship.endNodeElementId
      )
    }

  /** Attempts to parse a [[NeoPath]] as a [[Path]] */
  private def parseNeoPath(path: NeoPath): Either[ResultMapperException, Path] =
    traverseAs(List.iterableFactory[Path.Segment])(path.asScala.iterator) { segment =>
      (parseNeoNode(segment.start) and parseNeoRelationship(segment.relationship) and parseNeoNode(segment.end)).map {
        case ((start, relationship), end) =>
          Path.Segment(start, relationship, end)
      }
    } flatMap {
      case Nil =>
        parseNeoNode(path.start()).map { node =>
          Path.EmptyPath(node)
        }

      case segments: ::[Path.Segment] =>
        Right(Path.NonEmptyPath(segments))
    }

  /** Attempts to parse a [[NeoValue]] as a [[Value.TemporalInstantValue]]. */
  private def parseNeoTemporalValue[T <: Value.TemporalInstantValue](value: => T): Either[ResultMapperException, T] =
    Try(value).toEither.left.map { ex =>
      IncoercibleException(
        message = s"Error while parsing date value: ${value}",
        cause = Some(ex)
      )
    }
}
