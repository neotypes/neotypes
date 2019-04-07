package neotypes

import org.neo4j.driver.v1.types.{Path => NPath}

object types {
  final case class Path[N, R](nodes: Seq[N], relationships: Seq[R], path: NPath)
}
