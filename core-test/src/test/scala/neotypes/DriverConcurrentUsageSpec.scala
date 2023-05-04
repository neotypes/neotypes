package neotypes

import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.query.DeferredQueryBuilder

import org.scalatest.matchers.should.Matchers

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

/** Base class for testing the concurrent use of a Driver. */
trait BaseDriverConcurrentUsageSpec[F[_]] extends BaseIntegrationSpec[F] with Matchers { self: DriverProvider[F] with BaseAsyncSpec[F] =>
  behavior of s"Concurrent use of ${driverName}"

  // Use a custom EC to ensure the tasks run concurrently.
  override implicit final lazy val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  override protected final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY

  protected def runBothQueriesConcurrently(q1: DeferredQueryBuilder, q2: DeferredQueryBuilder): Future[Unit]

  it should "work and not throw an exception when the driver is used concurrently" in {
    def query(name: String): DeferredQueryBuilder =
      c"CREATE (p: PERSON { name: ${name} })"

    for {
      _ <- runBothQueriesConcurrently(query(name = "name1"), query(name = "name2"))
      result <- executeAsFuture { driver =>
        c"MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).list(driver)
      }
    } yield {
      result should contain theSameElementsAs List("name1", "name2")
    }
  }
}

final class AsyncDriverConcurrentUsageSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit) with BaseDriverConcurrentUsageSpec[F] {
  override protected def runBothQueriesConcurrently(q1: DeferredQueryBuilder, q2: DeferredQueryBuilder): Future[Unit] =
    executeAsFuture { driver =>
      def run(q: DeferredQueryBuilder): F[Unit] =
        q.execute.void(driver)

      runConcurrently(run(q1), run(q2))
    }
}

final class StreamDriverConcurrentUsageSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit) with BaseDriverConcurrentUsageSpec[F] {
  override protected def runBothQueriesConcurrently(q1: DeferredQueryBuilder, q2: DeferredQueryBuilder): Future[Unit] =
    executeAsFutureList { driver =>
      def run(q: DeferredQueryBuilder): S[Unit] =
        q.query(ResultMapper.ignore).stream(driver)

      streamConcurrently(run(q1), run(q2))
    }.void
}
