package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the basic behavoir of Stream[S, F] instances. */
final class StreamIntegrationSpec[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseStreamSpec(testkit) {
  behavior of s"Stream[${streamName}, ${effectName}]"

  it should s"execute a streaming query" in {
    executeAsFutureList { s =>
      "match (p: Person) return p.name"
        .query[Int]
        .stream(s)
    } map { names =>
      assert(names == (0 to 10).toList)
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
