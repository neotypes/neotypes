package neotypes

import model.types._

import org.neo4j.driver.{Record, Value}

import scala.jdk.CollectionConverters._

object Parser {
  /** Parses a Neo4j [[Record]] into a [[NeoMap]]. */
  def parseRecord(record: Record): NeoMap =
    NeoMap(
      properties = record.keys.asScala.view.map { key =>
        key -> parseValue(record.get(key))
      }.toMap
    )

  /** Parses a Neo4j [[Value]] into a [[NeoType]]. */
  def parseValue(value: Value): NeoType =
    ???
}
