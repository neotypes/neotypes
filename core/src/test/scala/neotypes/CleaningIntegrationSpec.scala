package neotypes

import neotypes.implicits.mappers.executions._
import neotypes.implicits.syntax.string._
import org.scalatest.FutureOutcome

import scala.concurrent.Future

/** Base class for integration specs that require to clean the graph after each test. */
abstract class CleaningIntegrationSpec[F[_]] extends BaseIntegrationSpec[F] {
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    val f = for {
      r <- super.withFixture(test).toFuture
      _ <- (new Driver[Future](driver)).writeSession { session =>
             "MATCH (n) DETACH DELETE n".query[Unit].execute(session)
           }
    } yield r
    new FutureOutcome(f)
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
