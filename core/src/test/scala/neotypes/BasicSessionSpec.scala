package neotypes

import neotypes.Async._
import neotypes.implicits.{StringExt, _}
import shapeless._

import scala.concurrent.Future
import BasicSessionSpec._
import org.neo4j.driver.v1.Value
import org.scalatest.AsyncFlatSpec

class BasicSessionSpec extends AsyncFlatSpec with BaseIntegrationSpec {

  it should "map result to hlist and case classes" in {
    val s = driver.session().asScala[Future]

    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
      int <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Int].single(s)
      long <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Long].single(s)
      double <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Double].single(s)
      float <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Float].single(s)
      notString <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[String].single(s).recover { case ex => ex.toString }
      cc <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Person].single(s)
      cc2 <- "match (p:Person {name: 'Charlize Theron'}) return p.born as born, p.name as name".query[Person2].single(s)
      map <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Map[String, Value]].single(s)
      emptyResult <- "match (p:Person {name: '1243'}) return p.born".query[Option[Int]].single(s)
      emptyResultList <- "match (p:Person {name: '1243'}) return p.born".query[Int].list(s)
      emptyResultEx <- "match (p:Person {name: '1243'}) return p.name".query[String].single(s).recover { case ex => ex.toString }
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
      assert(cc2.name.contains("Charlize Theron"))
      assert(cc2.born == 1975)
      assert(hlist.size == 1)
      assert(hlist.head.head.name.contains("Charlize Theron"))
      assert(hlist.head.last.title == "That Thing You Do")
      assert(map("name").asString == "Charlize Theron")
      assert(map("born").asInt == 1975)
      assert(emptyResult.isEmpty)
      assert(emptyResultList.isEmpty)
      assert(emptyResultEx == "neotypes.excpetions.PropertyNotFoundException: Property  not found") // TODO test separately
      assert(notString == "neotypes.excpetions.UncoercibleException: Cannot coerce INTEGER to Java String") // TODO test separately

    }
  }

  it should "map result to tuples" in {
    val s = driver.session().asScala[Future]

    for {
      tuple <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].list(s)
    } yield {
      assert(tuple.head._1.name.contains("Charlize Theron"))
      assert(tuple.head._2.title == "That Thing You Do")
    }
  }

  it should "map result to a case class with list" in {
    val s = driver.session().asScala[Future]

    for {
      сс3 <-
        """
          MATCH (movie:Movie {title: 'That Thing You Do'})
                 OPTIONAL MATCH (movie)<-[r]-(person:Person)
                 RETURN movie.title as title, collect({name:person.name, job:head(split(lower(type(r)),'_')), role:head(r.roles)}) as cast
                 LIMIT 1
        """.query[Movie2].single(s)

      ссOption <-
        """
          MATCH (movie:Movie {title: 'That Thing You Do'})
                 OPTIONAL MATCH (movie)<-[r]-(person:Person)
                 RETURN movie.title as title, collect({name:person.name, job:head(split(lower(type(r)),'_')), role:head(r.roles)}) as cast
                 LIMIT 1
        """.query[Option[Movie2]].single(s)
    } yield {
      assert(сс3.title == "That Thing You Do")
      assert(сс3.cast.size == 1)
      assert(сс3.cast.head.job == "acted")
      assert(сс3.cast.head.name == "Charlize Theron")
      assert(сс3.cast.head.role == "Tina")

      assert(ссOption.isDefined)
      assert(ссOption.get.title == "That Thing You Do")
      assert(ссOption.get.cast.size == 1)
    }
  }

  override val initQuery: String = BasicSessionSpec.INIT_QUERY
}

object BasicSessionSpec {

  case class Person(id: Long, born: Int, name: Option[String], f: Option[Int])

  case class Person2(born: Int, name: Option[String])

  case class Movie(id: Long, released: Int, title: String)

  case class Cast(name: String, job: String, role: String)

  case class Movie2(title: String, cast: List[Cast])

  val INIT_QUERY =
    """
      |CREATE (Charlize:Person {name:'Charlize Theron', born:1975})
      |CREATE (ThatThingYouDo:Movie {title:'That Thing You Do', released:1996, tagline:'In every life there comes a time when that thing you dream becomes that thing you do'})
      |CREATE (Charlize)-[:ACTED_IN {roles:['Tina']}]->(ThatThingYouDo)
      |CREATE (t:Test {added: date('2018-11-26')})
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)
    """.stripMargin
}
