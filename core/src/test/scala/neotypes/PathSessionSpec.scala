package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.types.{Node, Relationship}
import shapeless._

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class PathSessionSpec extends BaseIntegrationSpec[Future] {
  import PathSessionSpec._

  it should "map path to Seq" in execute { s =>
    for {
      path <- "match path=(:Person)-[*]->() return path".query[types.Path[Node, Relationship]].list(s)
      pathHList <- "match path=(p:Person)-[*]->() return p, path limit 1".query[Person :: types.Path[Node, Relationship] :: HNil].single(s)
    } yield {
      assert(path.size == 2)
      assert(pathHList.head == Person("Charlize Theron"))
      assert(pathHList.last.nodes.size == 2)
    }
  }

  it should "assign path to case class field" in execute { s =>
    "match path=(_:Person)-[*]->() return { path: path }".query[Data].list(s).map { res =>
      assert(res.size == 2)

      assert(res.head.path.nodes.size == 2)
      assert(res.head.path.nodes.flatMap(i => i.labels().asScala.toList) == List("Person", "Movie"))
      assert(res.head.path.relationships.size == 1)
      assert(res.head.path.relationships(0).`type`() == "ACTED_IN")

      assert(res.last.path.nodes.size == 3)
      assert(res.last.path.nodes.flatMap(i => i.labels().asScala.toList) == List("Person", "Movie", "Test"))
      assert(res.last.path.relationships.size == 2)
      assert(res.last.path.relationships(0).`type`() == "ACTED_IN")
      assert(res.last.path.relationships(1).`type`() == "TEST_EDGE")
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object PathSessionSpec {
  final case class Person(name: String)

  final case class Data(path: types.Path[Node, Relationship])
}
