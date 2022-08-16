package neotypes

import neotypes.internal.syntax.async._
import neotypes.implicits.syntax.cypher._
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

/** Base class for testing the concurrent use of a Driver. */
final class ConcurrentDriverSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] with Matchers {
  behavior of s"Concurrent use of Driver[${effectName}]"

  // Use a custom ec to ensure the tasks run concurrently.
  override implicit final lazy val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      java.util.concurrent.Executors.newFixedThreadPool(2)
    )

  it should "work and not throw an exception when the driver is used concurrently" in {
    executeAsFuture { d =>
      def query(name: String): F[Unit] =
        c"CREATE (p: PERSON { name: ${name} })".query.execute(d)

      runConcurrently(query(name = "name1"), query(name = "name2")).flatMap { _ =>
        c"MATCH (p: PERSON) RETURN p.name".readOnlyQuery[String].list(d)
      }
    } map { result =>
      result should contain theSameElementsAs List("name1", "name2")
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
