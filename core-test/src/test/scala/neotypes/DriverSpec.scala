package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.Node
import org.scalatest.Inspectors
import shapeless._

import java.time.ZonedDateTime

/** Base class for testing the basic behaviour of Driver[F] instances. */
final class DriverSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] with Inspectors {
  behavior of s"Driver[${effectName}]"

  import DriverSpec._

  it should "map result to simple values" in executeAsFuture { d =>
    for {
      string <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".readOnlyQuery[String].single(d)
      int <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".readOnlyQuery[Int].single(d)
      long <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".readOnlyQuery[Long].single(d)
      float <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".readOnlyQuery[Float].single(d)
      double <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".readOnlyQuery[Double].single(d)
      node <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".readOnlyQuery[Node].single(d)
    } yield {
      assert(string == "Charlize Theron")
      assert(int == 1975)
      assert(long == 1975L)
      assert((float - 1975.0f).abs < 0.0001f)
      assert((double - 1975.0d).abs < 0.0001d)
      assert(node.get("name").asString == "Charlize Theron")
    }
  }

  it should "map result to hlist and case classes" in executeAsFuture { d =>
    for {
      cc <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".readOnlyQuery[Person].single(d)
      cc2 <- "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born as born, p.name as name".readOnlyQuery[Person2].single(d)
      hlist <- "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".readOnlyQuery[Person :: Movie :: HNil].single(d)
    } yield {
      assert(cc.name.contains("Charlize Theron"))
      assert(cc.born == 1975)
      assert(cc.f.isEmpty)
      assert(cc2.name.contains("Charlize Theron"))
      assert(cc2.born == 1975)
      assert(hlist.head.name.contains("Charlize Theron"))
      assert(hlist.last.title == "That Thing You Do")
    }
  }

  it should "map empty result to a single option" in executeAsFuture { d =>
    "MATCH (p: Person { name: '1243' }) RETURN p.born".readOnlyQuery[Option[Int]].single(d).map { emptyResult =>
      assert(emptyResult.isEmpty)
    }
  }

  it should "map empty result to an empty list" in executeAsFuture { d =>
    "MATCH (p: Person { name: '1243' }) RETURN p.born".readOnlyQuery[Int].list(d).map { emptyResultList =>
      assert(emptyResultList.isEmpty)
    }
  }

  it should "lift exceptions into failed effects" in {
    recoverToExceptionIf[exceptions.IncoercibleException] {
      executeAsFuture { d =>
        "MATCH (p: Person { name: 'Charlize Theron'}) RETURN p.born".readOnlyQuery[String].single(d)
      }
    } map { ex =>
      assert(ex.getMessage == "Cannot coerce INTEGER to Java String for field [p.born] with value [1975]")
    }

    recoverToExceptionIf[exceptions.PropertyNotFoundException] {
      executeAsFuture { d =>
        "MATCH (p: Person { name: '1243' }) RETURN p.name".readOnlyQuery[String].single(d)
      }
    } map { ex =>
      assert(ex.getMessage == "Property  not found")
    }
  }

  it should "map result to tuples" in executeAsFuture { d =>
    for {
      tuple <- "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".readOnlyQuery[(Person, Movie)].list(d)
      tuplePrimitives <- "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p.name, m.title".readOnlyQuery[(String, String)].list(d)
    } yield {
      assert(tuple.head._1.name.contains("Charlize Theron"))
      assert(tuple.head._2.title == "That Thing You Do")

      assert(tuplePrimitives.head._1 == "Charlize Theron")
      assert(tuplePrimitives.head._2 == "That Thing You Do")
    }
  }

  it should "map result to a case class with list" in executeAsFuture { d =>
    for {
      сс3 <-
        """
        MATCH (movie: Movie { title: 'That Thing You Do' })
        OPTIONAL MATCH (movie)<-[r]-(person: Person)
        RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
        LIMIT 1
        """.readOnlyQuery[Movie2].single(d)

      ccOption <-
        """
        MATCH (movie: Movie { title: 'That Thing You Do' })
        OPTIONAL MATCH (movie)<-[r]-(person: Person)
        RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
        LIMIT 1
        """.readOnlyQuery[Option[Movie2]].single(d)
    } yield {
      assert(сс3.title == "That Thing You Do")
      assert(сс3.cast.size == 1)
      assert(сс3.cast.head.job == "acted")
      assert(сс3.cast.head.name == "Charlize Theron")
      assert(сс3.cast.head.role == "Tina")

      assert(ccOption.isDefined)
      assert(ccOption.get.title == "That Thing You Do")
      assert(ccOption.get.cast.size == 1)
      assert(ccOption.get.cast.head.job == "acted")
      assert(ccOption.get.cast.head.name == "Charlize Theron")
      assert(ccOption.get.cast.head.role == "Tina")
    }
  }

  it should "map result with relationship to a case class" in executeAsFuture { d =>
    for {
      hlist <-
        """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
        RETURN p, r
        LIMIT 1
        """.readOnlyQuery[Person :: Roles :: HNil].single(d)

      cc <-
        """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
        RETURN p as person, r as roles
        LIMIT 1
        """.readOnlyQuery[PersonWithRoles].single(d)
    } yield {
      assert(hlist.head.name.get == "Charlize Theron")
      assert(hlist.tail.head.roles == List("Tina"))
      assert(cc.person.name.get == "Charlize Theron")
      assert(cc.roles.roles == List("Tina"))
    }
  }

  it should "correctly handle date fields" in executeAsFuture { d =>
    val now = ZonedDateTime.now()
    val start = now.minusDays(10)
    val end = now.minusDays(30)

    for {
      _ <- c"CREATE (: DateFields { name: 'test', start: ${start}, end: ${end} })".query[Unit].execute(d)
      result <- c"MATCH (n: DateFields { name: 'test' }) RETURN n".readOnlyQuery[DateFields].single(d)
    } yield {
      assert(result.start == start)
      assert(result.end == end)
    }
  }

  it should "catch nulls and missing fields" in {
    val queries: List[Driver[F] => F[Option[String]]] = List(
      "RETURN NULL".readOnlyQuery[Option[String]].single(_),
      "RETURN NULL".readOnlyQuery[Option[String]].list(_).map(_.headOption.flatten),
      "RETURN NULL AS name".readOnlyQuery[WrappedName].single(_).map(_.name),
      "RETURN NULL AS name".readOnlyQuery[WrappedName].list(_).map(_.headOption.flatMap(_.name)),
      "RETURN 0".readOnlyQuery[WrappedName].single(_).map(_.name),
      "RETURN 0".readOnlyQuery[WrappedName].list(_).map(_.headOption.flatMap(_.name)),
      "RETURN NULL".readOnlyQuery[WrappedName].single(_).map(_.name),
      "RETURN NULL".readOnlyQuery[WrappedName].list(_).map(_.headOption.flatMap(_.name))
    )

    forAll(queries) { readOnlyQuery =>
      executeAsFuture { d =>
        readOnlyQuery(d).map { opt =>
          assert(opt.isEmpty)
        }
      }
    }
  }

  it should "correctly handle id fields" in executeAsFuture { d =>
    for {
      _ <- "CREATE (n: WithId { name: 'node1' })".query[Unit].execute(d)
      _ <- "CREATE (n: WithId { name: 'node2', id: '135' })".query[Unit].execute(d)
      _ <- "CREATE (n: WithId { name: 'node3', _id: '135' })".query[Unit].execute(d)
      _ <- "CREATE (n: WithId { name: 'node4', id: '135', _id: '531' })".query[Unit].execute(d)
      node1 <- "MATCH (n: WithId { name: 'node1' }) RETURN n, elementId(n)".readOnlyQuery[(WithId, String)].single(d)
      node2 <- "MATCH (n: WithId { name: 'node2' }) RETURN n, elementId(n)".readOnlyQuery[(WithId, String)].single(d)
      node3 <- "MATCH (n: WithId { name: 'node3' }) RETURN n, elementId(n)".readOnlyQuery[(WithId, String)].single(d)
      node4 <- "MATCH (n: WithId { name: 'node4' }) RETURN n, elementId(n)".readOnlyQuery[(WithId, String)].single(d)
    } yield {
      // Node 1 doesn't have any custom id property.
      // Thus the id field should contain the neo4j id.
      // and the _id field should also contain the neo4j id.
      assert(node1._1.id == node1._2)
      assert(node1._1._id == node1._2)

      // Node 2 has a custom id property.
      // Thus the id field should contain the custom id,
      // and the _id field should contain the neo4j id.
      assert(node2._1.id == "135")
      assert(node2._1._id == node2._2)

      // Node 3 has a custom _id property.
      // Thus the id field should contain the neo4j id,
      // and the _id field should contain the custom id.
      assert(node3._1.id == node3._2)
      assert(node3._1._id == "135")

      // Node 4 has both a custom id & _id properties.
      // Thus both properties should contain the custom ids,
      // and the system id is unreachable.
      assert(node4._1.id == "135")
      assert(node4._1._id == "531")
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object DriverSpec {
  final case class Person(born: Int, name: Option[String], f: Option[Int])

  final case class Person2(born: Int, name: Option[String])

  final case class Movie(released: Int, title: String)

  final case class Cast(name: String, job: String, role: String)

  final case class Movie2(title: String, cast: List[Cast])

  final case class Roles(roles: List[String])

  final case class PersonWithRoles(person: Person, roles: Roles)

  final case class DateFields(name: String, start: ZonedDateTime, end: ZonedDateTime)

  final case class WrappedName(name: Option[String])

  final case class WithId(id: String, name: String, _id: String)
}
