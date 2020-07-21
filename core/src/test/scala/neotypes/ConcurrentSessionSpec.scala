package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import org.neo4j.driver.exceptions.ClientException

/** Base class for testing the concurrent use of a session. */
final class ConcurrentSessionSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationSpec[F](testkit) {
  behavior of s"Concurrent use of Session[${effectName}]"

  it should "work and not throw an exception when a single session is used concurrently" in {
    executeAsFuture { s =>
      def query(name: String): DeferredQuery[Unit] =
        c"CREATE (p: PERSON { name: ${name} })".query[Unit]

      runConcurrently(
        query(name = "name1").execute(s),
        query(name = "name2").execute(s)
      )
    } map { _ =>
      succeed
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
