package neotypes

import com.dimafeng.testcontainers.{ForAllTestContainer, Neo4jContainer}
import neotypes.internal.utils.toJavaDuration
import org.neo4j.{driver => neo4j}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.images.PullPolicy
import scala.concurrent.Future
import scala.concurrent.duration._

/** Base class for writing integration specs. */
trait BaseIntegrationSpec[F[_]] extends BaseEffectSpec[F] with AsyncFlatSpecLike with ForAllTestContainer { self: SessionProvider[F] =>
  protected def initQuery: String

  override final val container =
    Neo4jContainer(neo4jImageVersion = "neo4j:latest")
      .configure(_.withoutAuthentication())
      .configure(_.addEnv("NEO4JLABS_PLUGINS", "[\"graph-data-science\"]"))
      .configure(_.withImagePullPolicy(PullPolicy.ageBased(toJavaDuration(1.day))))

  protected final lazy val driver =
    neo4j.GraphDatabase.driver(container.boltUrl)

  override final def afterStart(): Unit = {
    // Force evaluation of the driver and the session.
    neotypes.internal.utils.void(self.neotypesSession)
  }

  override final def beforeStop(): Unit = {
    driver.close()
  }

  private final def runQuery(query: String): Future[Unit] = {
    import neotypes.implicits.all._
    fToFuture(query.query[Unit].execute(self.neotypesSession))
  }

  private final var initWasRun = false
  private final def run[T](f: => Future[T]): Future[T] =
    if (initWasRun || initQuery == null) f
    else {
      initWasRun = true
      runQuery(initQuery).flatMap(_ => f)
    }

  protected final def cleanDB(): Future[Unit] = {
    runQuery(query = "MATCH (n) DETACH DELETE n")
  }

  protected final def executeAsFuture[T](work: self.neotypesSession.type => F[T]): Future[T] =
    run(fToFuture(work(self.neotypesSession)))
}

object BaseIntegrationSpec {
  final val DEFAULT_INIT_QUERY: String =
    """CREATE (Charlize: Person { name: 'Charlize Theron', born:1975 })
      |CREATE (ThatThingYouDo: Movie { title: 'That Thing You Do', released: 1996, tagline: 'In every life there comes a time when that thing you dream becomes that thing you do' })
      |CREATE (Charlize)-[:ACTED_IN { roles: ['Tina'] }]->(ThatThingYouDo)
      |CREATE (t: Test { added: date('2018-11-26') })
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)""".stripMargin

  final val MULTIPLE_VALUES_INIT_QUERY: String =
    (0 to 10).map(n => s"CREATE (:Person { name: ${n} })").mkString("\n")

  final val EMPTY_INIT_QUERY: String =
    null
}

trait SessionProvider[F[_]] { self: BaseIntegrationSpec[F] =>
  protected def sessionType: String
  protected val neotypesSession: Session[F]
}
