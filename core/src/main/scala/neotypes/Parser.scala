package neotypes

import internal.utils.traverseAs
import mappers.ResultMapper
import model.exceptions.{ResultMapperException, IncoercibleException}
import model.types._

import org.neo4j.driver.{Record => NeoRecord, Value => NeoValue}
import org.neo4j.driver.types.TypeSystem

import scala.jdk.CollectionConverters._

object Parser {
  val Types = TypeSystem.getDefault

  /** Parses and decodes a [[NeoRecord]] into a value using a [[ResultMapper]]. */
  def decodeRecord[A](record: NeoRecord, mapper: ResultMapper[A]): Either[ResultMapperException, A] =
    parseRecord(record).flatMap(mapper.decode)

  /** Parses a Neo4j [[NeoRecord]] into a [[NeoMap]]. */
  def parseRecord(record: NeoRecord): Either[ResultMapperException, NeoMap] =
    traverseAs(Map.mapFactory[String, NeoType])(record.keys.asScala.iterator) { key =>
      parseValue(value = record.get(key)).map { value =>
        key -> value
      }
    }.map(NeoMap.apply)

  /** Parses a Neo4j [[NeoValue]] into a [[NeoType]]. */
  def parseValue(value: NeoValue): Either[ResultMapperException, NeoType] =
    if (value.hasType(Types.INTEGER))
      Right(Value.Integer(value.asLong))
    else if (value.hasType(Types.NULL))
      Right(Value.NullValue)
    else
      Left(IncoercibleException(
        message = s"Unknown type '${value.`type`}' for value: ${value}"
      ))
}
