package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.types.Node
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
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
      string.loneElement shouldBe "Charlize Theron"
      int.loneElement shouldBe 1975
      long.loneElement shouldBe 1975
      node.loneElement.get("name").asString shouldBe "Charlize Theron"
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
      cc.loneElement.id should be >= 0L
      cc.loneElement.name.contains("Charlize Theron") shouldBe true
      cc.loneElement.born shouldBe 1975
      cc.loneElement.f.isEmpty shouldBe  true
      cc2.loneElement.name.contains("Charlize Theron") shouldBe true
      cc2.loneElement.born shouldBe 1975
      hlist.size shouldBe 1
      hlist.loneElement.head.name.contains("Charlize Theron") shouldBe true
      hlist.loneElement.last.title shouldBe "That Thing You Do"
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
      tuple.loneElement._1.name.contains("Charlize Theron") shouldBe true
      tuple.loneElement._2.title shouldBe "That Thing You Do"
      tuplePrimitives.loneElement._1 shouldBe "Charlize Theron"
      tuplePrimitives.loneElement._2 shouldBe "That Thing You Do"
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
      сс3.loneElement.title shouldBe "That Thing You Do"
      сс3.loneElement.cast.size shouldBe 1
      сс3.loneElement.cast.head.job shouldBe "acted"
      сс3.loneElement.cast.head.name shouldBe "Charlize Theron"
      сс3.loneElement.cast.head.role shouldBe "Tina"
      ссOption.loneElement.isDefined shouldBe true
      ссOption.loneElement.get.title shouldBe "That Thing You Do"
      ссOption.loneElement.get.cast.size shouldBe 1
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
      hlist.loneElement.head.name.get shouldBe "Charlize Theron"
      hlist.loneElement.tail.head.roles shouldBe List("Tina")
      cc.loneElement.person.name.get shouldBe "Charlize Theron"
      cc.loneElement.roles.roles shouldBe List("Tina")
    }
  }

  it should "catch nulls and missing fields" in {
      for{
        r1 <- executeAsFutureList{ "RETURN NULL".query[Option[String]].stream(_) }
        r2 <- executeAsFutureList{ "RETURN NULL AS name".query[WrappedName].stream(_) }
        r3 <- executeAsFutureList{ "RETURN 0".query[WrappedName].stream(_) }
        r4 <- executeAsFutureList{ "RETURN NULL".query[WrappedName].stream(_) }
      } yield {
        r1.loneElement.isEmpty shouldBe true
        r2.loneElement.name.isEmpty  shouldBe true
        r3.loneElement.name.isEmpty shouldBe true
        r4.loneElement.name.isEmpty shouldBe true
      }
  }

  it should "correctly handle id fields" in {
    for {
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node1' })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node2', id: 135 })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node3', _id: 135 })".query[Unit].stream(_) }
      _ <- executeAsFutureList{ "CREATE (n: WithId { name: 'node4', id: 135, _id: 531 })".query[Unit].stream(_) }
      node1 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node1' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }
      node2 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node2' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }
      node3 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node3' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }
      node4 <- executeAsFutureList{ "MATCH (n: WithId { name: 'node4' }) RETURN n, id(n)".query[(WithId, Int)].stream(_) }
    } yield {
      // Node 1 doesn't have any custom id property.
      // Thus the id field should contain the neo4j id.
      // and the _id field should also contain the neo4j id.
      node1.loneElement._1.id shouldBe node1.loneElement._2
      node1.loneElement._1._id shouldBe node1.loneElement._2

      // Node 2 has a custom id property.
      // Thus the id field should contain the custom id,
      // and the _id field should contain the neo4j id.
      node2.loneElement._1.id shouldBe 135
      node2.loneElement._1._id shouldBe node2.loneElement._2

      // Node 3 has a custom _id property.
      // Thus the id field should contain the neo4j id,
      // and the _id field should contain the custom id.
      node3.loneElement._1.id shouldBe node3.loneElement._2
      node3.loneElement._1._id shouldBe 135

      // Node 4 has both a custom id & _id properties.
      // Thus both properties should contain the custom ids,
      // and the system id is unreachable.
      node4.loneElement._1.id shouldBe 135
      node4.loneElement._1._id shouldBe 531
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}


