package neotypes

import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper

import org.scalatest.matchers.should.Matchers

trait BaseDriverSpec[F[_]] extends CleaningIntegrationSpec[F] with Matchers { self: DriverProvider[F] with BaseEffectSpec[F] =>
  it should "test" in executeAsFuture { driver =>
    for {
      i <- "RETURN 10".query(ResultMapper.int).single(driver)
    } yield {
      i shouldBe 0
    }
  }
}

final class AsyncDriverSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider(testkit) with BaseDriverSpec[F] {
  behavior of s"Driver[${effectName}]"
}

final class StreamingDriverSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider(testkit) with BaseDriverSpec[F] {
  behavior of s"StreamingDriver[${streamName}, ${effectName}]"

  it should "stream test" in {
    executeAsFutureList { driver =>
      "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(driver)
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }
}
