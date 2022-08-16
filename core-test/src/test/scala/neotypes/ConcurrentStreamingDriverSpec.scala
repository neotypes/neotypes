package neotypes

import neotypes.implicits.syntax.cypher._
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

/** Base class for testing the concurrent use of a StreamingDriver. */
final class ConcurrentStreamingDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider(testkit) with BaseIntegrationSpec[F] with Matchers {
  behavior of s"Concurrent use of StreamingDriver[${streamName}, ${effectName}]"

  // Use a custom ec to ensure the tasks run concurrently.
  override implicit final lazy val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      java.util.concurrent.Executors.newFixedThreadPool(2)
    )

  it should "work and not throw an exception when the streaming driver is used concurrently" in {
    executeAsFutureList { d =>
      def insertPerson(name: String): S[Unit] =
        c"CREATE (p: PERSON { name: ${name} })".query[Unit].stream(d)

      streamConcurrently(insertPerson(name = "name1"), insertPerson(name = "name2"))
    } flatMap { _ =>
      executeAsFuture{ d =>
        c"MATCH (p: PERSON) RETURN p.name".query[String].list(d)
      }
    } map { result =>
      result should contain theSameElementsAs List("name1", "name2")
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
