package neotypes

import neotypes.implicits.mappers.executions._
import neotypes.implicits.syntax.string._
import org.scalatest.FutureOutcome
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

/** Base class for integration specs that require to clean the graph after each test. */
abstract class CleaningIntegrationWordSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationWordSpec(testkit) {
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    complete {
      super.withFixture(test)
    } lastly {
      this.cleanDb()
    }
  }

  override final val initQuery: String = BaseIntegrationWordSpec.EMPTY_INIT_QUERY
}
