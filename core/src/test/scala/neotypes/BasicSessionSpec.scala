package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.types.Node
import org.scalatest.matchers.should.Matchers
import shapeless._

import scala.concurrent.Future

/** Base class for testing the basic behaviour of Session[F] instances. */
class BasicSessionSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationWordSpec(testkit) with Matchers {

  import BasicSessionSpec._
  s"Session[${effectName}]" should {
    def personQuery(nameField: String = "Charlize Theron", returnAttribute: String = "p.born") = s"match (p:Person {name: '${nameField}'}) return ${returnAttribute}"
    "map result to string" in executeAsFuture(s => personQuery(returnAttribute = "p.name").query[String].single(s).map(_ shouldBe "Charlize Theron"))

    "map result to int" in executeAsFuture(s => personQuery().query[Int].single(s).map(_ shouldBe 1975))

    "map result to long" in executeAsFuture(s => personQuery().query[Long].single(s).map(_ should === (1975L)))

    "map result to double" in executeAsFuture(s => personQuery().query[Double].single(s).map(_ should === (1975D)))

    "map result to float" in executeAsFuture(s => personQuery().query[Float].single(s).map(_ should === (1975F)))

    "recover from exception" in executeAsFuture(s =>  personQuery().query[String].single(s)
      .recover { case ex => ex.toString }).map(_ shouldBe  "neotypes.exceptions$IncoercibleException: Cannot coerce INTEGER to Java String for field [p.born] with value [1975]") // TODO test separately

    "map result to case class" in executeAsFuture(s => personQuery(returnAttribute = "p").query[Person].single(s)).map{ cc =>
      cc.id should be >= 0l
      cc.name should contain ("Charlize Theron")
      cc.born shouldBe 1975
      cc.f shouldBe None
    }

    "map result components to case class" in executeAsFuture(s => personQuery(returnAttribute = "p.born as born, p.name as name").query[Person2].single(s)).map{ cc2 =>
      cc2.name should contain("Charlize Theron")
      cc2.born shouldBe 1975
    }

    "map result to option" in executeAsFuture(s =>  personQuery(nameField = "1243").query[Option[Int]].single(s)).map(_ shouldBe None)

    "map result to list" in executeAsFuture(s =>  personQuery(nameField = "1243").query[Int].list(s)).map(_ shouldBe Nil)

    "map result to hlist" in executeAsFuture(s =>  "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[Person :: Movie :: HNil].list(s)).map{ hlist =>
      hlist.size shouldBe 1
      hlist.head.head.name should contain("Charlize Theron")
      hlist.head.last.title shouldBe "That Thing You Do"
    }

    "map result to node" in executeAsFuture(s => personQuery(returnAttribute = "p").query[Node].list(s)).map(_.head.get("name").asString() shouldBe "Charlize Theron")

    "map result to tuples" in executeAsFuture { s =>
      for {
        tuple <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p,m".query[(Person, Movie)].list(s)
        tuplePrimitives <- "match (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) return p.name,m.title".query[(String, String)].list(s)
      } yield {
        tuple.head._1.name should contain("Charlize Theron")
        tuple.head._2.title shouldBe "That Thing You Do"
        tuplePrimitives.head._1 shouldBe "Charlize Theron"
        tuplePrimitives.head._2 shouldBe "That Thing You Do"
      }
    }

    "map result to a case class with list" in executeAsFuture { s =>
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
        сс3.title shouldBe "That Thing You Do"
        сс3.cast.size shouldBe 1
        сс3.cast.head.job shouldBe "acted"
        сс3.cast.head.name shouldBe "Charlize Theron"
        сс3.cast.head.role shouldBe "Tina"

        ссOption should not be None
        ссOption.get.title shouldBe "That Thing You Do"
        ссOption.get.cast.size shouldBe 1
      }
    }

    "map result with relationship to a case class" in executeAsFuture { s =>
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
        hlist.head.name.get shouldBe "Charlize Theron"
        hlist.tail.head.roles shouldBe List("Tina")
        cc.person.name.get shouldBe "Charlize Theron"
        cc.roles.roles shouldBe List("Tina")
      }
    }

    "catch nulls and missing fields" in {
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
      val res = queries.foldLeft(Future.successful(List.empty[Option[String]])) {
        case (accF, query) =>
          for {
            acc <- accF
            e <- executeAsFuture(s => query(s))
          } yield e :: acc
      }.map{ results =>
        results.map(_ shouldBe None)
      }

      res.map(_ should contain only succeed)
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
}
