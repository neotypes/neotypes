package neotypes

import neotypes.internal.utils.{toJavaDuration, void}

import com.dimafeng.testcontainers.{ForAllTestContainer, Neo4jContainer}
import org.neo4j.{driver => neo4j}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.testcontainers.images.{ImagePullPolicy, PullPolicy}
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration._

/** Base class for writing integration specs. */
trait BaseIntegrationSpec[F[_]] extends AsyncFlatSpecLike with ForAllTestContainer { self: DriverProvider[F] =>
  protected def initQuery: String

  override final val container =
    Neo4jContainer(neo4jImageVersion = DockerImageName.parse("neo4j:latest"))
      .configure(c => void(c.withoutAuthentication))
      .configure(c => void(c.addEnv("NEO4JLABS_PLUGINS", "[\"graph-data-science\"]")))
      .configure(c => void(c.withImagePullPolicy(imagePullPolicy)))

  private def imagePullPolicy: ImagePullPolicy =
    util.Properties.envOrNone(name = "CI") match {
      case Some("true") => PullPolicy.ageBased(toJavaDuration(1.day))
      case _ => PullPolicy.defaultPolicy
    }

  protected lazy final val neoDriver =
    neo4j.GraphDatabase.driver(
      container.boltUrl,
      neo4j.Config.builder
        .withLogging(neo4j.Logging.slf4j)
        .withDriverMetrics
        .build()
    )

  private final def runQuery(query: String): Unit = {
    val s = neoDriver.session
    s.executeWrite(
      new neo4j.TransactionCallback[Unit] {
        override def execute(tx: neo4j.TransactionContext): Unit = {
          void(tx.run(query))
        }
      }
    )
    s.close()
  }

  override final def afterStart(): Unit = {
    // Force evaluation of the driver.
    void(self.driver)

    if (initQuery != null) {
      runQuery(initQuery)
    }
  }

  protected final def cleanDB(): Unit = {
    runQuery("MATCH (n) DETACH DELETE n")
  }

  override final def beforeStop(): Unit = {
    neoDriver.close()
  }
}

object BaseIntegrationSpec {
  final val DEFAULT_INIT_QUERY: String =
    """CREATE (Charlize: Person { name: 'Charlize Theron', born: 1975 })
      |CREATE (ThatThingYouDo: Movie { title: 'That Thing You Do', released: 1996, tagline: 'In every life there comes a time when that thing you dream becomes that thing you do' })
      |CREATE (Charlize)-[: ACTED_IN { roles: ['Tina'] }]->(ThatThingYouDo)
      |CREATE (t: Test { added: date('2018-11-26') })
      |CREATE (ThatThingYouDo)-[: TEST_EDGE]->(t)""".stripMargin

  final val MULTIPLE_VALUES_INIT_QUERY: String =
    (0 to 10).map(n => s"CREATE (: Person { name: $n })").mkString("\n")

  final val EMPTY_INIT_QUERY: String =
    null
}

trait DriverProvider[F[_]] { self: BaseIntegrationSpec[F] =>
  type DriverType <: Driver[F]
  protected val driver: DriverType
}
