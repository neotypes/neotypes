package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.{Node, Relationship}

import scala.jdk.CollectionConverters._
import shapeless._
import org.scalatest.matchers.should.Matchers._

/** Base class for testing the extraction of Paths. */
final class PathSessionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  import PathSessionSpec._

  s"Extracting a Path using: ${effectName}" should {
    "map path to Seq" in executeAsFuture { d =>
      for {
        path <- "MATCH path=(: Person)-[*]->() RETURN path".query[types.Path[Node, Relationship]].list(d)
        pathHList <- "MATCH path=(p: Person)-[*]->() RETURN p, path limit 1".query[Person :: types.Path[Node, Relationship] :: HNil].single(d)
      } yield {
        path.size shouldBe 2
        pathHList.head shouldBe Person("Charlize Theron")
        pathHList.last.nodes.size shouldBe 2
      }
    }
    "assign path to case class field" in executeAsFuture { d =>
      "MATCH path=(: Person)-[*]->() RETURN { path: path }".query[Data].list(d).map { res =>
        res.size shouldBe 2
        res.head.path.nodes.size shouldBe 2
        res.head.path.nodes.flatMap(i => i.labels.asScala.toList)  shouldBe List("Person", "Movie")
        res.head.path.relationships.size shouldBe 1
        res.head.path.relationships(0).`type` shouldBe "ACTED_IN"

        res.last.path.nodes.size shouldBe 3
        res.last.path.nodes.flatMap(i => i.labels.asScala.toList) shouldBe List("Person", "Movie", "Test")
        res.last.path.relationships.size shouldBe 2
        res.last.path.relationships(0).`type` shouldBe "ACTED_IN"
        res.last.path.relationships(1).`type` shouldBe "TEST_EDGE"
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object PathSessionSpec {
  final case class Person(name: String)

  final case class Data(path: types.Path[Node, Relationship])
}
