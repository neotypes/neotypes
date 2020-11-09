package neotypes

import com.dimafeng.testcontainers.{ForAllTestContainer, Neo4jContainer}
import neotypes.internal.utils.toJavaDuration
import org.neo4j.{driver => neo4j}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.testcontainers.images.PullPolicy
import scala.concurrent.Future
import scala.concurrent.duration._

/** Base class for writing integration specs. */
abstract class BaseIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends BaseEffectSpec(testkit) with AsyncFlatSpecLike with ForAllTestContainer  {
  protected def initQuery: String

  override final val container =
    Neo4jContainer(neo4jImageVersion = "neo4j:latest")
      .configure(_.withoutAuthentication())
      .configure(_.addEnv("NEO4JLABS_PLUGINS", "[\"graph-data-science\"]"))
      .configure(_.withImagePullPolicy(PullPolicy.ageBased(toJavaDuration(1.day))))

  protected lazy final val driver =
    neo4j.GraphDatabase.driver(container.boltUrl)

  private lazy final val neo4jSession =
    driver.session()

  private lazy final val neotypesSession =
    Session[F](F, driver.asyncSession())(fToT(F.makeLock))

  private final def runQuery(query: String): Unit = {
    neo4jSession.writeTransaction(
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
    driver.close()
  }

  protected final def cleanDb(): Unit = {
    runQuery("MATCH (n) DETACH DELETE n")
  }

  protected final def executeAsFuture[T](work: Session[F] => F[T]): Future[T] =
    fToFuture(work(neotypesSession))
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
