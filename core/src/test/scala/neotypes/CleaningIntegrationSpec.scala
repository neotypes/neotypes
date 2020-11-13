package neotypes

import org.scalatest.FutureOutcome

/** Base class for integration specs that require to clean the graph after each test. */
trait CleaningIntegrationSpec[F[_]] extends BaseIntegrationSpec[F] { self: SessionProvider[F] =>
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    val r = for {
      o <- super.withFixture(test).toFuture
      _ <- this.cleanDB()
    } yield o

    new FutureOutcome(r)
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
