package neotypes.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Interval
import neotypes.{refined => _, _}
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.IncoercibleException
import neotypes.syntax.all._
import neotypes.refined.mappers._

/** Base class for testing the use of the library with Refined. */
sealed trait BaseRefinedSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  import BaseRefinedSpec._

  behavior of s"${driverName} with Refined types"

  it should "work with refined types" in executeAsFuture { driver =>
    val level: Level = 5

    for {
      _ <- c"CREATE (: Level { value: ${level} })".execute.void(driver)
      r <- "MATCH (level: Level) RETURN level.value".query(Level.resultMapper).single(driver)
    } yield {
      r shouldBe level
    }
  }

  it should "fail if trying to retrieve a non-valid value for a refined type" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN 0".query(Level.resultMapper).single(driver)
      }
    }
  }
}

object BaseRefinedSpec {
  type Level = Int Refined Interval.Closed[1, 99]
  object Level {
    val resultMapper = refined[Level](ResultMapper.int)
  }
}

final class AsyncRefinedSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseRefinedSpec[F]

final class StreamRefinedSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseRefinedSpec[F]
