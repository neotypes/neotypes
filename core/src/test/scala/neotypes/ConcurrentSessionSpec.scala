package neotypes

import neotypes.internal.syntax.async._
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import org.neo4j.driver.exceptions.ClientException
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

/** Base class for testing the concurrent use of a session. */
final class ConcurrentSessionSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationWordSpec[F](testkit) with Matchers {

  // Use a custom ec to ensure the tasks run concurrently.
  override implicit final def executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      java.util.concurrent.Executors.newFixedThreadPool(2)
    )
  s"Concurrent use of Session[${effectName}]" should {
    "work and not throw an exception when a single session is used concurrently" in {
      executeAsFuture { s =>
        def query(name: String): F[Unit] =
          c"CREATE (p: PERSON { name: ${name} })".query[Unit].execute(s)

        runConcurrently(query(name = "name1"), query(name = "name2")).flatMap { _ =>
          c"MATCH (p: PERSON) RETURN p.name".query[String].list(s)
        }
      } map { result =>
        result should contain theSameElementsAs List("name1", "name2")
      }
    }
  }

  override final val initQuery: String = BaseIntegrationWordSpec.EMPTY_INIT_QUERY
}
