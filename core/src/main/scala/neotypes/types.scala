package neotypes

object types {
  final class Path[NODE, RELATIONSHIP](val nodes: Seq[NODE], val relationships: Seq[RELATIONSHIP], path: org.neo4j.driver.v1.types.Path)
}
