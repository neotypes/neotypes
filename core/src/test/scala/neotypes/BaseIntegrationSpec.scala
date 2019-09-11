package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.neo4j.driver.{v1 => neo4j}
import org.scalatest.AsyncFlatSpec
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

/** Base class for simple integration specs. */
abstract class BaseIntegrationSpec[F[_]] extends AsyncFlatSpec with ForAllTestContainer {
  def initQuery: String

  override val container = GenericContainer("neo4j:3.5.3",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(30))
  )

  lazy val driver = neo4j.GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  def execute[T](work: Session[F] => F[T])
                (implicit F: Async[F]): F[T] =
    (new Driver[F](driver)).writeSession(work)

  override def afterStart(): Unit = {
    if (initQuery != null) {
      val session = driver.session()
      try {
        session.writeTransaction(
          new neo4j.TransactionWork[Unit] {
            override def execute(tx: neo4j.Transaction): Unit =
              tx.run(initQuery)
          }
        )
      } finally {
        session.close()
      }
    }
  }
}

object BaseIntegrationSpec {
  final val DEFAULT_INIT_QUERY: String =
    """CREATE (Charlize:Person {name:'Charlize Theron', born:1975})
      |CREATE (ThatThingYouDo:Movie {title:'That Thing You Do', released:1996, tagline:'In every life there comes a time when that thing you dream becomes that thing you do'})
      |CREATE (Charlize)-[:ACTED_IN {roles:['Tina']}]->(ThatThingYouDo)
      |CREATE (t:Test {added: date('2018-11-26')})
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)""".stripMargin

  final val MULTIPLE_VALUES_INIT_QUERY: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n")

  final val EMPTY_INIT_QUERY: String =
    null
}
