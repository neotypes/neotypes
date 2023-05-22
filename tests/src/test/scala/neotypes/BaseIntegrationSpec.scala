package neotypes

import neotypes.internal.utils.void

import com.dimafeng.testcontainers.{ForAllTestContainer, Neo4jContainer}
import org.neo4j.{driver => neo4j}
import org.scalatest.FutureOutcome
import org.testcontainers.images.{ImagePullPolicy, PullPolicy}
import org.testcontainers.utility.DockerImageName

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

/** Marker trait to signal that the test needs a driver. */
trait DriverProvider[F[_]] extends { self: BaseIntegrationSpec[F] =>
  protected type DriverType <: AsyncDriver[F]
  protected type TransactionType <: AsyncTransaction[F]

  protected def driverName: String
  protected def transactionName: String

  protected def driver: DriverType

  protected def transaction[T](driver: DriverType): F[TransactionType]

  protected def transact[T](driver: DriverType)(txF: TransactionType => F[T]): F[T]
}

/** Base class for writing integration specs. */
trait BaseIntegrationSpec[F[_]] extends BaseAsyncSpec[F] with ForAllTestContainer { self: DriverProvider[F] =>
  protected def initQuery: String

  override final val container =
    Neo4jContainer(neo4jImageVersion = DockerImageName.parse("neo4j:latest"))
      .configure(c => void(c.withoutAuthentication))
      .configure(c => void(c.addEnv("NEO4J_PLUGINS", "[\"graph-data-science\"]")))
      .configure(c => void(c.withImagePullPolicy(imagePullPolicy)))

  private def imagePullPolicy: ImagePullPolicy =
    util.Properties.envOrNone(name = "CI") match {
      case Some("true") =>
        PullPolicy.ageBased(1.day.toJava)

      case _ =>
        PullPolicy.defaultPolicy
    }

  protected lazy final val neoDriver =
    neo4j
      .GraphDatabase
      .driver(
        container.boltUrl,
        neo4j.Config.builder.withLogging(neo4j.Logging.slf4j).withDriverMetrics.build()
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

  protected final def executeAsFuture[A](work: DriverType => F[A]): Future[A] =
    fToFuture(work(self.driver))

  protected final def debugMetrics(): F[Unit] =
    F.map(self.driver.metrics) { metrics =>
      println(s"METRICS: ${metrics}")
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

/** Base class for integration specs that require to clean the graph after each test. */
trait CleaningIntegrationSpec[F[_]] extends BaseIntegrationSpec[F] { self: DriverProvider[F] =>
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    complete {
      super.withFixture(test)
    } lastly {
      this.cleanDB()
    }
  }

  override protected final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
