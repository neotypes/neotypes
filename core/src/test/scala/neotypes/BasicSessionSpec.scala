package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.types.Node
import shapeless._

import scala.concurrent.Future

class BasicSessionSpec extends BaseIntegrationSpec[Future] {
  import BasicSessionSpec._

  it should "map result to hlist and case classes" in execute { s =>
    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
      int <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Int].single(s)
      long <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Long].single(s)
      double <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Double].single(s)
      float <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Float].single(s)
      notString <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[String].single(s).recover { case ex => ex.toString }
      cc <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Person].single(s)
      cc2 <- "match (p:Person {name: 'Charlize Theron'}) return p.born as born, p.name as name".query[Person2].single(s)
      emptyResult <- "match (p:Person {name: '1243'}) return p.born".query[Option[Int]].single(s)
      emptyResultList <- "match (p:Person {name: '1243'}) return p.born".query[Int].list(s)
      emptyResultEx <- "match (p:Person {name: '1243'}) return p.name".query[String].single(s).recover { case ex => ex.toString }
      hlist <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)
      node <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Node].list(s)
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
      assert(emptyResult.isEmpty)
      assert(emptyResultList.isEmpty)
      assert(emptyResultEx == "neotypes.exceptions$PropertyNotFoundException: Property  not found") // TODO test separately
      assert(notString == "neotypes.exceptions$IncoercibleException: Cannot coerce INTEGER to Java String") // TODO test separately
      assert(node.head.get("name").asString() == "Charlize Theron")
    }
  }

  it should "map result to tuples" in execute { s =>
    for {
      tuple <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].list(s)
      tuplePrimitives <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p.name,m.title".query[(String, String)].list(s)
    } yield {
      assert(tuple.head._1.name.contains("Charlize Theron"))
      assert(tuple.head._2.title == "That Thing You Do")
      assert(tuplePrimitives.head._1 == "Charlize Theron")
      assert(tuplePrimitives.head._2 == "That Thing You Do")
    }
  }

  it should "map result to a case class with list" in execute { s =>
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

  it should "map result with relationship to a case class" in execute { s =>
    for {
      hlist <-
        """
        MATCH (p:Person)-[r:ACTED_IN]->(:Movie)
               RETURN p, r
               LIMIT 1
        """.query[Person :: Roles :: HNil].single(s)
      cc <-
        """
        MATCH (p:Person)-[r:ACTED_IN]->(:Movie)
               RETURN p as person, r as roles
               LIMIT 1
        """.query[PersonWithRoles].single(s)
    } yield {
      assert(hlist.head.name.get == "Charlize Theron")
      assert(hlist.tail.head.roles == List("Tina"))
      assert(cc.person.name.get == "Charlize Theron")
      assert(cc.roles.roles == List("Tina"))
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object BasicSessionSpec {
  final case class Person(id: Long, born: Int, name: Option[String], f: Option[Int])

  final case class Person2(born: Int, name: Option[String])

  final case class Movie(id: Long, released: Int, title: String)

  final case class Cast(name: String, job: String, role: String)

  final case class Movie2(title: String, cast: List[Cast])

  final case class Roles(roles: List[String])

  final case class PersonWithRoles(person: Person, roles: Roles)
}
