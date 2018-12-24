package neotypes

import neotypes.Async._
import neotypes.implicits.{StringExt, _}
import org.neo4j.driver.v1.types.{Node, Relationship}
import shapeless._
import PathSessionSpec._
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class PathSessionSpec extends AsyncFlatSpec with BaseIntegrationSpec {

  it should "map path to Seq" in {
    val s = driver.session().asScala[Future]

    for {
      path <- "match path=(:Person)-[*]->() return path".query[types.Path[Node, Relationship]].list(s)
      pathHList <- "match path=(p:Person)-[*]->() return p, path limit 1".query[Person :: types.Path[Node, Relationship] :: HNil].single(s)
    } yield {
      assert(path.size == 2)
      assert(pathHList.head == Person("Charlize Theron"))
      assert(pathHList.last.nodes.size == 2)
    }
  }
  override val initQuery: String = PathSessionSpec.INIT_QUERY
}

object PathSessionSpec {

  case class Person(name: String)

  val INIT_QUERY =
    """
      |CREATE (Charlize:Person {name:'Charlize Theron', born:1975})
      |CREATE (ThatThingYouDo:Movie {title:'That Thing You Do', released:1996, tagline:'In every life there comes a time when that thing you dream becomes that thing you do'})
      |CREATE (Charlize)-[:ACTED_IN {roles:['Tina']}]->(ThatThingYouDo)
      |CREATE (t:Test {added: date('2018-11-26')})
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)
    """.stripMargin
}
