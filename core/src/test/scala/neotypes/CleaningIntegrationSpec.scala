package neotypes

import neotypes.implicits.mappers.executions._
import neotypes.implicits.syntax.driver._
import neotypes.implicits.syntax.string._
import org.scalatest.FutureOutcome

import scala.concurrent.Future

/** Base class for integration specs that require to clean the graph after each test. */
abstract class CleaningIntegrationSpec extends BaseIntegrationSpec {
  override final def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    val f = for {
      r <- super.withFixture(test).toFuture
      _ <- driver.asScala[Future].writeSession { s =>
             "MATCH (n) DETACH DELETE n".query[Unit].execute(s)
           }
    } yield r
    new FutureOutcome(f)
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
