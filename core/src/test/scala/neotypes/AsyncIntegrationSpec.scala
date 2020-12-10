package neotypes

import neotypes.implicits.syntax.string._
import org.neo4j.driver.exceptions.ClientException

/** Base class for testing the basic behavoir of Async[F] instances. */
final class AsyncIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationSpec(testkit) {
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
