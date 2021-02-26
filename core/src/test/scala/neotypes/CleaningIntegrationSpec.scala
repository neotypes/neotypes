package neotypes

import org.scalatest.FutureOutcome

/** Base class for integration specs that require to clean the graph after each test. */
trait CleaningIntegrationSpec[F[_]] extends BaseIntegrationSpec[F] { self: DriverProvider[F] =>
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    complete {
      super.withFixture(test)
    } lastly {
      this.cleanDB()
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
