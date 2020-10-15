package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.Node
import shapeless._
import scala.concurrent.Future

/** Base class for testing the basic behaviour of Session[F] instances. */
trait BasicSessionSpec[F[_]] extends BaseIntegrationSpec[F] { self: SessionProvider[F] =>
  behavior of s"${sessionType}[${effectName}]"

  import BasicSessionSpec._

  it should "map result to hlist and case classes" in executeAsFuture { s =>
    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
      int <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Int].single(s)
      long <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Long].single(s)
      double <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Double].single(s)
      float <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[Float].single(s)
      cc <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Person].single(s)
      cc2 <- "match (p:Person {name: 'Charlize Theron'}) return p.born as born, p.name as name".query[Person2].single(s)
      hlist <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)
      node <- "match (p:Person {name: 'Charlize Theron'}) return p".query[Node].list(s)
      notString <- "match (p:Person {name: 'Charlize Theron'}) return p.born".query[String].single(s).recover { case ex => ex.toString }
      emptyResult <- "match (p:Person {name: '1243'}) return p.born".query[Option[Int]].single(s)
      emptyResultList <- "match (p:Person {name: '1243'}) return p.born".query[Int].list(s)
      emptyResultEx <- "match (p:Person {name: '1243'}) return p.name".query[String].single(s).recover { case ex => ex.toString }
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
      assert(node.head.get("name").asString() == "Charlize Theron")
      assert(notString == "neotypes.exceptions$IncoercibleException: Cannot coerce INTEGER to Java String for field [p.born] with value [1975]") // TODO test separately
      assert(emptyResult.isEmpty)
      assert(emptyResultList.isEmpty)
      assert(emptyResultEx == "neotypes.exceptions$PropertyNotFoundException: Property  not found") // TODO test separately
    }
  }

  it should "map result to tuples" in executeAsFuture { s =>
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

  it should "map result to a case class with list" in executeAsFuture { s =>
    for {
      сс3 <-
        """
          MATCH (movie:Movie {title: 'That Thing You Do'})
                 OPTIONAL MATCH (movie)<-[r]-(person:Person)
                 RETURN movie.title as title, collect({name:person.name, job:head(split(toLower(type(r)),'_')), role:head(r.roles)}) as cast
                 LIMIT 1
        """.query[Movie2].single(s)

      ссOption <-
        """
          MATCH (movie:Movie {title: 'That Thing You Do'})
                 OPTIONAL MATCH (movie)<-[r]-(person:Person)
                 RETURN movie.title as title, collect({name:person.name, job:head(split(toLower(type(r)),'_')), role:head(r.roles)}) as cast
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

  it should "map result with relationship to a case class" in executeAsFuture { s =>
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

  it should "catch nulls and missing fields" in {
    val queries: List[Session[F] => F[Option[String]]] = List(
      "RETURN NULL".query[Option[String]].single(_),
      "RETURN NULL".query[Option[String]].list(_).map(_.headOption.flatten),
      "RETURN NULL AS name".query[WrappedName].single(_).map(_.name),
      "RETURN NULL AS name".query[WrappedName].list(_).map(_.headOption.flatMap(_.name)),
      "RETURN 0".query[WrappedName].single(_).map(_.name),
      "RETURN 0".query[WrappedName].list(_).map(_.headOption.flatMap(_.name)),
      "RETURN NULL".query[WrappedName].single(_).map(_.name),
      "RETURN NULL".query[WrappedName].list(_).map(_.headOption.flatMap(_.name))
    )

    // Custom Future.traverse that runs sequentially.
    queries.foldLeft(Future.successful(List.empty[Option[String]])) {
      case (accF, query) =>
        for {
          acc <- accF
          e <- executeAsFuture(s => query(s))
        } yield e :: acc
    } flatMap { results =>
      assert(results.forall(_ == None))
    }
  }

  it should "correctly handle id fields" in executeAsFuture { s =>
    for {
      _ <- "CREATE (n: WithId { name: 'node1' })".query[Unit].execute(s)
      _ <- "CREATE (n: WithId { name: 'node2', id: 135 })".query[Unit].execute(s)
      _ <- "CREATE (n: WithId { name: 'node3', _id: 135 })".query[Unit].execute(s)
      _ <- "CREATE (n: WithId { name: 'node4', id: 135, _id: 531 })".query[Unit].execute(s)
      node1 <- "MATCH (n: WithId { name: 'node1' }) RETURN n, id(n)".query[(WithId, Int)].single(s)
      node2 <- "MATCH (n: WithId { name: 'node2' }) RETURN n, id(n)".query[(WithId, Int)].single(s)
      node3 <- "MATCH (n: WithId { name: 'node3' }) RETURN n, id(n)".query[(WithId, Int)].single(s)
      node4 <- "MATCH (n: WithId { name: 'node4' }) RETURN n, id(n)".query[(WithId, Int)].single(s)
    } yield {
      // Node 1 doesn't have any custom id property.
      // Thus the id field should contain the neo4j id.
      // and the _id field should also contain the neo4j id.
      assert(node1._1.id == node1._2)
      assert(node1._1._id == node1._2)

      // Node 2 has a custom id property.
      // Thus the id field should contain the custom id,
      // and the _id field should contain the neo4j id.
      assert(node2._1.id == 135)
      assert(node2._1._id == node2._2)

      // Node 3 has a custom _id property.
      // Thus the id field should contain the neo4j id,
      // and the _id field should contain the custom id.
      assert(node3._1.id == node3._2)
      assert(node3._1._id == 135)

      // Node 4 has both a custom id & _id properties.
      // Thus both properties should contain the custom ids,
      // and the system id is unreachable.
      assert(node4._1.id == 135)
      assert(node4._1._id == 531)
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object BasicSessionSpec {
  final case class Person(id: Long, born: Int, name: Option[String], f: Option[Int])

  final case class Person2(born: Int, name: Option[String])

  final case class Movie(id: Long, released: Int, title: String)

  final case class Cast(name: String, job: String, role: String)

  final case class Movie2(title: String, cast: List[Cast])

  final case class Roles(roles: List[String])

  final case class PersonWithRoles(person: Person, roles: Roles)

  final case class WrappedName(name: Option[String])

  final case class WithId(id: Int, name: String, _id: Int)
}
