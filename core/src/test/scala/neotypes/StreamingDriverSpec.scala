package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.types.Node
import org.scalatest.{LoneElement, OptionValues}
import org.scalatest.matchers.should.Matchers
import shapeless._


/** Base class for testing the basic behaviour of StreamingDriver[S, F] instances. */
final class StreamingDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider[S, F](testkit) with BaseIntegrationSpec[F] with Matchers with LoneElement with OptionValues {
  behavior of s"StreamingDriver[${streamName}, ${effectName}]"

  import DriverSpec._

  it should "map result to simple values" in {
    for {
      string <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].stream(d))
      int <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Int].stream(d))
      long <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Long].stream(d))
      float <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Float].stream(d))
      double <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Double].stream(d))
      node <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Node].stream(d))
    } yield {
      string.loneElement shouldBe "Charlize Theron"
      int.loneElement shouldBe 1975
      long.loneElement shouldBe 1975L
      (float.loneElement - 1975.0f).abs should be < 0.0001f
      (double.loneElement - 1975.0d).abs should be < 0.0001d
      node.loneElement.get("name").asString shouldBe "Charlize Theron"
    }
  }

  it should "map result to hlist and case classes" in {
    for {
      cc <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Person].stream(d))
      cc2 <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born as born, p.name as name".query[Person2].stream(d))
      hlist <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".query[Person :: Movie :: HNil].stream(d))
    } yield {
      cc.loneElement.id should be >= 0L
      cc.loneElement.name should contain ("Charlize Theron")
      cc.loneElement.born shouldBe 1975
      cc.loneElement.f.isEmpty shouldBe  true

      cc2.loneElement.name should contain ("Charlize Theron")
      cc2.loneElement.born shouldBe 1975

      hlist.loneElement.head.name should contain ("Charlize Theron")
      hlist.loneElement.last.title shouldBe "That Thing You Do"
    }
  }

  it should "map empty result to a single option" in {
    executeAsFutureList { d =>
      "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Option[Int]].stream(d)
    } map { emptyResult =>
      emptyResult shouldBe empty
    }
  }

  it should "map empty result to an empty list" in {
    executeAsFutureList { d =>
      "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Int].stream(d)
    } map { emptyResultList =>
      emptyResultList shouldBe empty
    }
  }

  it should "lift exceptions into failed effects" in {
    recoverToExceptionIf[exceptions.IncoercibleException] {
      executeAsFutureList { d =>
        "MATCH (p: Person { name: 'Charlize Theron'}) RETURN p.born".query[String].stream(d)
      }
    } map { ex =>
      ex.getMessage shouldBe "Cannot coerce INTEGER to Java String for field [p.born] with value [1975]"
    }
  }

  it should "map result to tuples" in {
    for {
      tuple <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".query[(Person, Movie)].stream(d))
      tuplePrimitives <- executeAsFutureList(d => "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p.name, m.title".query[(String, String)].stream(d))
    } yield {
      tuple.loneElement._1.name should contain ("Charlize Theron")
      tuple.loneElement._2.title shouldBe "That Thing You Do"

      tuplePrimitives.loneElement._1 shouldBe "Charlize Theron"
      tuplePrimitives.loneElement._2 shouldBe "That Thing You Do"
    }
  }

  it should "map result to a case class with list" in {
    for {
      сс3 <- executeAsFutureList { d =>
        """
        MATCH (movie: Movie { title: 'That Thing You Do' })
        OPTIONAL MATCH (movie)<-[r]-(person: Person)
        RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
        LIMIT 1
        """.query[Movie2].stream(d)
      }

      ccOption <- executeAsFutureList { d =>
        """
        MATCH (movie: Movie { title: 'That Thing You Do' })
        OPTIONAL MATCH (movie)<-[r]-(person: Person)
        RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
        LIMIT 1
        """.query[Option[Movie2]].stream(d)
      }
    } yield {
      сс3.loneElement.title shouldBe "That Thing You Do"
      сс3.loneElement.cast.loneElement.job shouldBe "acted"
      сс3.loneElement.cast.loneElement.name shouldBe "Charlize Theron"
      сс3.loneElement.cast.loneElement.role shouldBe "Tina"

      ccOption.loneElement.value.title shouldBe "That Thing You Do"
      ccOption.loneElement.value.cast.loneElement.job shouldBe "acted"
      ccOption.loneElement.value.cast.loneElement.name shouldBe "Charlize Theron"
      ccOption.loneElement.value.cast.loneElement.role shouldBe "Tina"
    }
  }

  it should "map result with relationship to a case class" in {
    for {
      hlist <- executeAsFutureList { d =>
        """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
        RETURN p, r
        LIMIT 1
        """.query[Person :: Roles :: HNil].stream(d)
      }

      cc <- executeAsFutureList { d =>
        """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
        RETURN p as person, r as roles
        LIMIT 1
        """.query[PersonWithRoles].stream(d)
      }
    } yield {
      hlist.loneElement.head.name.value shouldBe "Charlize Theron"
      hlist.loneElement.tail.head.roles.loneElement shouldBe "Tina"

      cc.loneElement.person.name.value shouldBe "Charlize Theron"
      cc.loneElement.roles.roles.loneElement shouldBe "Tina"
    }
  }

  it should "catch nulls and missing fields" in {
    for{
      r1 <- executeAsFutureList(d => "RETURN NULL".query[Option[String]].stream(d))
      r2 <- executeAsFutureList(d => "RETURN NULL AS name".query[WrappedName].stream(d))
      r3 <- executeAsFutureList(d => "RETURN 0".query[WrappedName].stream(d))
      r4 <- executeAsFutureList(d => "RETURN NULL".query[WrappedName].stream(d))
    } yield {
      r1.loneElement shouldBe None
      r2.loneElement.name shouldBe None
      r3.loneElement.name shouldBe None
      r4.loneElement.name shouldBe None
    }
  }

  it should "correctly handle id fields" in {
    for {
      _ <- executeAsFutureList(d => "CREATE (n: WithId { name: 'node1' })".query[Unit].stream(d))
      _ <- executeAsFutureList(d => "CREATE (n: WithId { name: 'node2', id: 135 })".query[Unit].stream(d))
      _ <- executeAsFutureList(d => "CREATE (n: WithId { name: 'node3', _id: 135 })".query[Unit].stream(d))
      _ <- executeAsFutureList(d => "CREATE (n: WithId { name: 'node4', id: 135, _id: 531 })".query[Unit].stream(d))
      node1List <- executeAsFutureList(d => "MATCH (n: WithId { name: 'node1' }) RETURN n, id(n)".query[(WithId, Int)].stream(d))
      node2List <- executeAsFutureList(d => "MATCH (n: WithId { name: 'node2' }) RETURN n, id(n)".query[(WithId, Int)].stream(d))
      node3List <- executeAsFutureList(d => "MATCH (n: WithId { name: 'node3' }) RETURN n, id(n)".query[(WithId, Int)].stream(d))
      node4List <- executeAsFutureList(d => "MATCH (n: WithId { name: 'node4' }) RETURN n, id(n)".query[(WithId, Int)].stream(d))
    } yield {
      // Node 1 doesn't have any custom id property.
      // Thus the id field should contain the neo4j id.
      // and the _id field should also contain the neo4j id.
      val node1 = node1List.loneElement
      node1._1.id shouldBe node1._2
      node1._1._id shouldBe node1._2

      // Node 2 has a custom id property.
      // Thus the id field should contain the custom id,
      // and the _id field should contain the neo4j id.
      val node2 = node2List.loneElement
      node2._1.id shouldBe 135
      node2._1._id shouldBe node2._2

      // Node 3 has a custom _id property.
      // Thus the id field should contain the neo4j id,
      // and the _id field should contain the custom id.
      val node3 = node3List.loneElement
      node3._1.id shouldBe node3._2
      node3._1._id shouldBe 135

      // Node 4 has both a custom id & _id properties.
      // Thus both properties should contain the custom ids,
      // and the system id is unreachable.
      val node4 = node4List.loneElement
      node4._1.id shouldBe 135
      node4._1._id shouldBe 531
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
