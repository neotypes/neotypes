package neotypes

import neotypes.implicits.syntax.string._
import org.neo4j.driver.exceptions.ClientException
import org.scalatest.matchers.should.Matchers

/** Base class for testing the basic behavior of Stream[S, F] instances. */
final class StreamSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider(testkit) with BaseIntegrationSpec[F] with Matchers {
  behavior of s"Stream[${streamName}, ${effectName}]"

  it should s"execute a streaming query" in {
    executeAsFutureList { d =>
      "match (p: Person) return p.name"
        .query[Int]
        .stream(d)
    } map { names =>
      names should contain theSameElementsAs (0 to 10)
    }
  }

  it should s"catch exceptions inside the stream" in {
    recoverToSucceededIf[ClientException] {
      executeAsFutureList { d =>
        "match test return p.name"
          .query[String]
          .stream(d)
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
