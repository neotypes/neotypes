package neotypes

import mappers.ParameterMapper

import org.neo4j.driver.v1.types.{Path => NPath}

object types {
  final case class Path[N, R](nodes: Seq[N], relationships: Seq[R], path: NPath)

  /** Safe wrapper over a Neo4j parameter. */
  final class NeoType private[neotypes] (private[neotypes] val underlying: AnyRef) extends AnyVal

  object NeoType {
    def apply[A](scalaValue: A)(implicit mapper: ParameterMapper[A]): NeoType =
      mapper.toNeoType(scalaValue)
  }
}
