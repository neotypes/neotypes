package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.types.Node
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import shapeless._


/** Base class for testing the basic behaviour of StreamingDriver[S, F] instances. */
final class StreamingDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider[S, F](testkit) with BaseIntegrationSpec[F] with Inspectors with Matchers{
  behavior of s"StreamingDriver[${effectName}]"

  import DriverSpec._

  it should "map result to simple values" in {
    for {
      string <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name".query[String].stream(_) }
      int <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Int].stream(_) }
      long <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Long].stream(_) }
      double <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Double].stream(_) }
      float <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born".query[Float].stream(_) }
      node <- executeAsFutureList { "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Node].stream(_) }
    } yield {
      string shouldBe List("Charlize Theron")
      int shouldBe List(1975)
      long shouldBe List(1975)
      node.head.get("name").asString shouldBe "Charlize Theron"
      (double.head - 1975).abs should be < 0.0001
      (float.head - 1975).abs should be < 0.0001F
    }
  }

  it should "map result to hlist and case classes" in {
    for {
      cc <- executeAsFutureList{ "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p".query[Person].stream(_) }
      cc2 <- executeAsFutureList{  "MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.born as born, p.name as name".query[Person2].stream(_) }
      hlist <- executeAsFutureList{ "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".query[Person :: Movie :: HNil].stream(_) }
    } yield {
      cc.head.id should be >= 0L
      cc.head.name.contains("Charlize Theron") shouldBe true
      cc.head.born shouldBe 1975
      cc.head.f.isEmpty shouldBe  true
      cc2.head.name.contains("Charlize Theron") shouldBe true
      cc2.head.born shouldBe 1975
      hlist.size shouldBe 1
      hlist.head.head.name.contains("Charlize Theron") shouldBe true
      hlist.head.last.title shouldBe "That Thing You Do"
    }
  }

  it should "map empty result to a single option" in {
    executeAsFutureList { "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Option[Int]].stream(_) }.map { emptyResult =>
      emptyResult.isEmpty shouldBe true
    }
  }

  it should "map empty result to an empty list" in {
    executeAsFutureList { d =>
      "MATCH (p: Person { name: '1243' }) RETURN p.born".query[Int].stream(d)
    }.map { emptyResultList =>
      emptyResultList.isEmpty shouldBe true
    }
  }

  it should "lift exceptions into failed effects" in {
    recoverToExceptionIf[exceptions.IncoercibleException] {
      executeAsFutureList {
        "MATCH (p: Person { name: 'Charlize Theron'}) RETURN p.born".query[String].stream(_)
      }
    } map { ex =>
      ex.getMessage shouldBe "Cannot coerce INTEGER to Java String for field [p.born] with value [1975]"
    }
  }

  it should "map result to tuples" in {
    for {
      tuple <- executeAsFutureList{ "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p, m".query[(Person, Movie)].stream(_) }
      tuplePrimitives <- executeAsFutureList{ "MATCH (p: Person { name: 'Charlize Theron' })-[]->(m: Movie) RETURN p.name, m.title".query[(String, String)].stream(_) }
    } yield {
      tuple.head._1.name.contains("Charlize Theron") shouldBe true
      tuple.head._2.title shouldBe "That Thing You Do"
      tuplePrimitives.head._1 shouldBe "Charlize Theron"
      tuplePrimitives.head._2 shouldBe "That Thing You Do"
    }
  }

  it should "map result to a case class with list" in {
    for {
      сс3 <-
        executeAsFutureList{ """
          MATCH (movie: Movie { title: 'That Thing You Do' })
                 OPTIONAL MATCH (movie)<-[r]-(person: Person)
                 RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
                 LIMIT 1
        """.query[Movie2].stream(_) }

      ссOption <-
      executeAsFutureList{
        """
          MATCH (movie: Movie { title: 'That Thing You Do' })
                 OPTIONAL MATCH (movie)<-[r]-(person: Person)
                 RETURN movie.title as title, collect({ name: person.name, job: head(split(toLower(type(r)),'_')), role: head(r.roles)}) as cast
                 LIMIT 1
        """.query[Option[Movie2]].stream(_) }
    } yield {
      сс3.head.title shouldBe "That Thing You Do"
      сс3.head.cast.size shouldBe 1
      сс3.head.cast.head.job shouldBe "acted"
      сс3.head.cast.head.name shouldBe "Charlize Theron"
      сс3.head.cast.head.role shouldBe "Tina"
      ссOption.head.isDefined shouldBe true
      ссOption.head.get.title shouldBe "That Thing You Do"
      ссOption.head.get.cast.size shouldBe 1
    }
  }

  it should "map result with relationship to a case class" in {
    for {
      hlist <-
        executeAsFutureList{ """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
               RETURN p, r
               LIMIT 1
        """.query[Person :: Roles :: HNil].stream(_) }
      cc <-
        executeAsFutureList{ """
        MATCH (p: Person)-[r: ACTED_IN]->(: Movie)
               RETURN p as person, r as roles
               LIMIT 1
        """.query[PersonWithRoles].stream(_) }
    } yield {
      hlist.head.head.name.get shouldBe "Charlize Theron"
      hlist.head.tail.head.roles shouldBe List("Tina")
      cc.head.person.name.get shouldBe "Charlize Theron"
      cc.head.roles.roles shouldBe List("Tina")
    }
  }

  it should "catch nulls and missing fields" in {
      for{
        r1 <- executeAsFutureList{ "RETURN NULL".query[Option[String]].stream(_) }
        r2 <- executeAsFutureList{ "RETURN NULL AS name".query[WrappedName].stream(_) }
        r3 <- executeAsFutureList{ "RETURN 0".query[WrappedName].stream(_) }
        r4 <- executeAsFutureList{ "RETURN NULL".query[WrappedName].stream(_) }
      } yield {
        r1.head.isEmpty shouldBe true
        r2.head.name.isEmpty  shouldBe true
        r3.head.name.isEmpty shouldBe true
        r4.head.name.isEmpty shouldBe true
      }
  }

  it should "correctly handle id fields" in {
    for {
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node1' })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node2', id: 135 })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node3', _id: 135 })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node4', id: 135, _id: 531 })".query[Unit].stream(_) }
      node1 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node1' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }.map(_.head)
      node2 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node2' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }.map(_.head)
      node3 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node3' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }.map(_.head)
      node4 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node4' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }.map(_.head)
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


