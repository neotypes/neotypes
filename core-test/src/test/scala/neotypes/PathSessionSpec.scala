package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.{Node, Relationship}
import scala.jdk.CollectionConverters._
import shapeless._

/** Base class for testing the extraction of Paths. */
final class PathSessionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  behavior of s"Extracting a Path using: ${effectName}"

  import PathSessionSpec._

  it should "map path to Seq" in executeAsFuture { d =>
    for {
      path <- "MATCH path=(: Person)-[*]->() RETURN path".query[types.Path[Node, Relationship]].list(d)
      pathHList <- "MATCH path=(p: Person)-[*]->() RETURN p, path limit 1".query[Person :: types.Path[Node, Relationship] :: HNil].single(d)
    } yield {
      assert(path.size == 2)
      assert(pathHList.head == Person("Charlize Theron"))
      assert(pathHList.last.nodes.size == 2)
    }
  }

  it should "assign path to case class field" in executeAsFuture { d =>
    "MATCH path=(: Person)-[*]->() RETURN { path: path }".query[Data].list(d).map { res =>
      assert(res.size == 2)

      assert(res.head.path.nodes.size == 2)
      assert(res.head.path.nodes.flatMap(i => i.labels.asScala.toList) == List("Person", "Movie"))
      assert(res.head.path.relationships.size == 1)
      assert(res.head.path.relationships(0).`type` == "ACTED_IN")

      assert(res.last.path.nodes.size == 3)
      assert(res.last.path.nodes.flatMap(i => i.labels.asScala.toList) == List("Person", "Movie", "Test"))
      assert(res.last.path.relationships.size == 2)
      assert(res.last.path.relationships(0).`type` == "ACTED_IN")
      assert(res.last.path.relationships(1).`type` == "TEST_EDGE")
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object PathSessionSpec {
  final case class Person(name: String)

  final case class Data(path: types.Path[Node, Relationship])
}
