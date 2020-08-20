package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.{Node, Relationship}
import scala.jdk.CollectionConverters._
import shapeless._
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import org.scalatest.matchers.should.Matchers._

/** Base class for testing the extraction of Paths. */
final class PathSessionSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationWordSpec(testkit) {
  import PathSessionSpec._
  s"Extracting a Path using: ${effectName}" should {
    "map path to Seq" in executeAsFuture { s =>
      for {
        path <- "match path=(:Person)-[*]->() return path".query[types.Path[Node, Relationship]].list(s)
        pathHList <- "match path=(p:Person)-[*]->() return p, path limit 1".query[Person :: types.Path[Node, Relationship] :: HNil].single(s)
      } yield {
        path.size shouldBe 2
        pathHList.head shouldBe Person("Charlize Theron")
        pathHList.last.nodes.size shouldBe 2
      }
    }

    "assign path to case class field" in executeAsFuture { s =>
      "match path=(_:Person)-[*]->() return { path: path }".query[Data].list(s).map { res =>
        res.size shouldBe 2

        res.head.path.nodes.size shouldBe 2
        res.head.path.nodes.flatMap(i => i.labels().asScala.toList) shouldBe List("Person", "Movie")
        res.head.path.relationships.size shouldBe 1
        res.head.path.relationships(0).`type`() shouldBe "ACTED_IN"

        res.last.path.nodes.size shouldBe 3
        res.last.path.nodes.flatMap(i => i.labels().asScala.toList) shouldBe List("Person", "Movie", "Test")
        res.last.path.relationships.size shouldBe 2
        res.last.path.relationships(0).`type`() shouldBe "ACTED_IN"
        res.last.path.relationships(1).`type`() shouldBe "TEST_EDGE"
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object PathSessionSpec {
  final case class Person(name: String)

  final case class Data(path: types.Path[Node, Relationship])
}
