package neotypes

import org.scalatest.FutureOutcome

/** Base class for integration specs that require to clean the graph after each test. */
abstract class CleaningIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationSpec(testkit) {
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    complete {
      super.withFixture(test)
    } lastly {
      this.cleanDb()
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
