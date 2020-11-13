package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.exceptions.ClientException
import org.scalatest.matchers.should.Matchers

/** Base class for testing the basic behaviour of Stream[S, F] instances. */
trait StreamIntegrationSpec[S[_], F[_]] extends BaseIntegrationSpec[F] with Matchers { self: StreamSessionProvider[S, F] =>
  behavior of s"Stream[${streamName}, ${effectName}]"

  it should s"execute a streaming query" in {
    executeAsFutureList { s =>
      "match (p: Person) return p.name"
        .query[Int]
        .stream(s)
    } map { names =>
      names should contain theSameElementsAs (0 to 10)
    }
  }

  it should s"catch exceptions inside the stream" in {
    recoverToSucceededIf[ClientException] {
      executeAsFutureList { s =>
        "match test return p.name"
          .query[String]
          .stream(s)
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
