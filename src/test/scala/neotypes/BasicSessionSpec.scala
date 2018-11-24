package neotypes

import neotypes.Async._
import neotypes.implicits.{StringExt, _}
import shapeless._

import scala.concurrent.Future
import BasicSessionSpec._
import org.neo4j.driver.v1.Value

class BasicSessionSpec extends BaseIntegrationSpec(BasicSessionSpec.INIT_QUERY) {
  it should "map result to hlist and case classes" in {
    val s = driver.session().asScala[Future]

    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
      int <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Int].single(s)
      long <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Long].single(s)
      double <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Double].single(s)
      float <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Float].single(s)
      cc <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Person].single(s)
      map <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Map[String, Value]].single(s)
      emptyResult <- "match (p:Person {name: '1243'}) return p.born".query[Option[Int]].single(s)
      emptyResultEx <- "match (p:Person {name: '1243'}) return p.name".query[String].single(s).recover{ case ex => ex.toString }
      hlist <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)
      //tuple <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].list(s)
    } yield {
      assert(string == "Charlize Theron")
      assert(int == 1975)
      assert(long == 1975)
      assert((double - 1975).abs < 0.0001)
      assert((float - 1975).abs < 0.0001)
      assert(cc.id >= 0)
      assert(cc.name.contains("Charlize Theron"))
      assert(cc.born == 1975)
      assert(cc.f.isEmpty)
      assert(hlist.size == 1)
      assert(hlist.head.head.name.contains("Charlize Theron"))
      assert(hlist.head.last.title == "That Thing You Do")
      assert(map("name").asString == "Charlize Theron")
      assert(map("born").asInt == 1975)
      assert(emptyResult.isEmpty)
      assert(emptyResultEx == "neotypes.excpetions.PropertyNotFoundException: Property  not found") // TODO test separately
      //assert(tuple.head._1.name.contains("Charlize Theron"))
      //assert(tuple.head._2.title == "That Thing You Do")

    }
  }
}

object BasicSessionSpec {

  case class Person(id: Long, born: Int, name: Option[String], f: Option[Int])

  case class Movie(id: Long, released: Int, title: String)

  val INIT_QUERY =
    """
      |CREATE (Charlize:Person {name:'Charlize Theron', born:1975})
      |CREATE (ThatThingYouDo:Movie {title:'That Thing You Do', released:1996, tagline:'In every life there comes a time when that thing you dream becomes that thing you do'})
      |CREATE (Charlize)-[:ACTED_IN {roles:['Tina']}]->(ThatThingYouDo)
      |CREATE (t:Test {added: date('2018-11-26')})
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)
    """.stripMargin
}
