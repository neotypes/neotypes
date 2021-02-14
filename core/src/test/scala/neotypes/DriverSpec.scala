package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.Node
import shapeless._
import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers._

/** Base class for testing the basic behaviour of Driver[F] instances. */
final class DriverSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  import DriverSpec._

  s"Driver[${effectName}]" should {
    def personQuery(nameField: String = "Charlize Theron", returnAttribute: String = "p.born") = s"match (p:Person {name: '${nameField}'}) return ${returnAttribute}"
    "map result to string" in executeAsFuture(s => personQuery(returnAttribute = "p.name").query[String].single(s).map(_ shouldBe "Charlize Theron"))
    "map result to int" in executeAsFuture(s => personQuery().query[Int].single(s).map(_ shouldBe 1975))
    "map result to long" in executeAsFuture(s => personQuery().query[Long].single(s).map(_ should === (1975L)))
    "map result to double" in executeAsFuture(s => personQuery().query[Double].single(s).map(_ should === (1975D)))
    "map result to float" in executeAsFuture(s => personQuery().query[Float].single(s).map(_ should === (1975F)))
    "map result to node" in executeAsFuture(d => personQuery(returnAttribute = "p").query[Node].list(d).map(node => node.head.get("name").asString shouldBe "Charlize Theron"))
    "map result to hlist" in executeAsFuture(s =>  "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)).map{ hlist =>
      hlist.size shouldBe 1
      hlist.head.head.name should contain("Charlize Theron")
      hlist.head.last.title shouldBe "That Thing You Do"
    }
    "map result to case class" in executeAsFuture(s => personQuery(returnAttribute = "p").query[Person].single(s)).map{ cc =>
      cc.id should be >= 0L
      cc.name should contain ("Charlize Theron")
      cc.born shouldBe 1975
      cc.f shouldBe None
    }
    "map result components to case class" in executeAsFuture(s => personQuery(returnAttribute = "p.born as born, p.name as name").query[Person2].single(s)).map{ cc2 =>
      cc2.name should contain("Charlize Theron")
      cc2.born shouldBe 1975
    }
    "map empty result to a single option" in executeAsFuture { d =>
      "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Option[Int]].single(d).map { emptyResult =>
        emptyResult shouldBe None
      }
    }
    "map empty result to an empty list" in executeAsFuture { d =>
      "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Int].list(d).map { emptyResultList =>
        emptyResultList shouldBe Nil
      }
    }
    "lift exceptions into failed effects" in {
      recoverToExceptionIf[exceptions.IncoercibleException] {
        executeAsFuture { d =>
          "MATCH (p: Person { name: 'Charlize Theron'}) RETURN p.born".query[String].single(d)
        }
      } map { ex =>
        ex.getMessage shouldBe "Cannot coerce INTEGER to Java String for field [p.born] with value [1975]"
      }

      recoverToExceptionIf[exceptions.PropertyNotFoundException] {
        executeAsFuture { d =>
          "MATCH (p: Person { name: '1243' }) RETURN p.name".query[String].single(d)
        }
      } map { ex =>
        ex.getMessage shouldBe "Property  not found"
      }
    }
    "map result to tuples" in executeAsFuture { d =>
      for {
        tuple <- "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".query[(Person, Movie)].list(d)
        tuplePrimitives <- "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p.name, m.title".query[(String, String)].list(d)
      } yield {
        tuple.head._1.name shouldBe Some("Charlize Theron")
        tuple.head._2.title shouldBe "That Thing You Do"
        tuplePrimitives.head._1 shouldBe "Charlize Theron"
        tuplePrimitives.head._2 shouldBe "That Thing You Do"
      }
    }
    "map result to a case class with list" in executeAsFuture { d =>
      for {
        сс3 <-
          """
          MATCH (movie: Movie { title: 'That Thing You Do' })
                 OPTIONAL MATCH (movie)<-[r]-(person: Person)
                 RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
                 LIMIT 1
        """.query[Movie2].single(d)

        ссOption <-
          """
          MATCH (movie: Movie { title: 'That Thing You Do' })
                 OPTIONAL MATCH (movie)<-[r]-(person: Person)
                 RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
                 LIMIT 1
        """.query[Option[Movie2]].single(d)
      } yield {
        сс3.title shouldBe "That Thing You Do"
        сс3.cast.size shouldBe 1
        сс3.cast.head.job shouldBe "acted"
        сс3.cast.head.name shouldBe "Charlize Theron"
        сс3.cast.head.role shouldBe "Tina"

        ссOption.isDefined shouldBe true
        ссOption.get.title shouldBe "That Thing You Do"
        ссOption.get.cast.size shouldBe 1
      }
    }
    "map result with relationship to a case class" in executeAsFuture { s =>
      for {
        hlist <-
          """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
               RETURN p, r
               LIMIT 1
        """.query[Person :: Roles :: HNil].single(s)
        cc <-
          """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
               RETURN p as person, r as roles
               LIMIT 1
        """.query[PersonWithRoles].single(s)
      } yield {
        hlist.head.name.get shouldBe "Charlize Theron"
        hlist.tail.head.roles shouldBe List("Tina")
        cc.person.name.get shouldBe "Charlize Theron"
        cc.roles.roles shouldBe List("Tina")
      }
    }
    "catch nulls and missing fields" in {
      val queries: List[Driver[F] => F[Option[String]]] = List(
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
      queries.iterator.zipWithIndex.foldLeft(Future.successful(succeed)) {
        case (accF, (query, idx)) =>
          accF.flatMap { _ =>
            executeAsFuture(s => query(s)).map {
              case None    => succeed
              case Some(x) => fail(s"query ${idx} RETURNed ${x} instead of None")
            }
          }
      }
    }
    "correctly handle id fields" in executeAsFuture { s =>
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
        node1._1.id shouldBe node1._2
        node1._1._id shouldBe node1._2

        // Node 2 has a custom id property.
        // Thus the id field should contain the custom id,
        // and the _id field should contain the neo4j id.
        node2._1.id shouldBe 135
        node2._1._id shouldBe node2._2

        // Node 3 has a custom _id property.
        // Thus the id field should contain the neo4j id,
        // and the _id field should contain the custom id.
        node3._1.id shouldBe node3._2
        node3._1._id shouldBe 135

        // Node 4 has both a custom id & _id properties.
        // Thus both properties should contain the custom ids,
        // and the system id is unreachable.
        node4._1.id shouldBe 135
        node4._1._id shouldBe 531
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}

object DriverSpec {
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
