package neotypes

import org.scalatest.FutureOutcome

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
