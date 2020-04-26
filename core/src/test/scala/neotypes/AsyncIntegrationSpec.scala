package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the basic behavoir of Async[F] instances. */
abstract class AsyncIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationSpec[F](testkit) {
  behavior of s"Async[${effectName}]"

  it should s"execute a simple query" in {
    executeAsFuture { s =>
      "match (p: Person { name: 'Charlize Theron' }) return p.name"
        .query[String]
        .single(s)
    } map { name =>
      assert(name == "Charlize Theron")
    }
  }

  it should s"catch exceptions" in {
    recoverToSucceededIf[ClientException] {
      executeAsFuture { s =>
        "match test return p.name"
          .query[String]
          .single(s)
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
