package neotypes.refined

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.IncoercibleException
import neotypes.syntax.all._
import neotypes.refined.mappers._

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Interval
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

final class RefinedSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] with Matchers {
  import RefinedSpec._

  behavior of s"${driverName} with Refined types"

  it should "work with refined types" in executeAsFuture { driver =>
    val level: Level = 5
    val mapper = refined[Level](ResultMapper.int)

    // Success.
    for {
      _ <- c"CREATE (: Level { value: ${level} })".execute.void(driver)
      r <- "MATCH (level: Level) RETURN level.value".query(mapper).single(driver)
    } yield {
      r shouldBe level
    }

    // Fail if retrieving a non-valid value.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN 0".query(mapper).single(driver)
    }
  }
}

object RefinedSpec {
  type Level = Int Refined Interval.Closed[1, 99]
}
