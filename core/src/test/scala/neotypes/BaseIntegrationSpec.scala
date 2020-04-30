package neotypes

import java.time.Duration
import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.neo4j.driver.{v1 => neo4j}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import scala.concurrent.Future

/** Base class for writing integration specs. */
abstract class BaseIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends BaseEffectSpec(testkit) with AsyncFlatSpecLike with ForAllTestContainer {
  protected def initQuery: String

  override final val container = GenericContainer("neo4j:3.5",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(30))
  ).configure(_.withClasspathResourceMapping("neo4j.conf", "/var/lib/neo4j/conf/", BindMode.READ_ONLY))
   .configure(_.withClasspathResourceMapping("graph-algorithms-algo-3.5.4.0.jar", "/var/lib/neo4j/plugins/", BindMode.READ_ONLY))

  protected lazy final val driver =
    neo4j.GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  protected lazy final val session =
    driver.session()

  private final def runQuery(query: String): Unit = {
    session.writeTransaction(
      new neo4j.TransactionWork[Unit] {
        override def execute(tx: neo4j.Transaction): Unit =
          tx.run(query)
      }
    )
  }

  override final def afterStart(): Unit = {
    if (initQuery != null) {
      runQuery(initQuery)
    }
  }

  override final def beforeStop(): Unit = {
    session.close()
    driver.close()
  }

  protected final def cleanDb(): Unit = {
    runQuery("MATCH (n) DETACH DELETE n")
  }

  protected final def execute[T](work: Session[F] => F[T])(implicit F: Async[F]): F[T] =
    work((new Session[F](session)))

  protected final def executeAsFuture[T](work: Session[F] => F[T]): Future[T] =
    fToFuture(execute(work))
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
