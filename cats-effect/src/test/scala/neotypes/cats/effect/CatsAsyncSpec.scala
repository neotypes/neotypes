package neotypes.cats.effect

import cats.effect.IO
import neotypes.BaseIntegrationSpec
import neotypes.cats.effect.implicits._
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.exceptions.ClientException

class CatsAsyncSpec extends BaseIntegrationSpec[IO] {
  it should "work with cats.effect.IO" in execute { s =>
    """match (p:Person {name: "Charlize Theron"}) return p.name"""
      .query[String]
      .single(s)
  }.unsafeToFuture().map {
    name => assert(name == "Charlize Theron")
  }

  it should "catch exceptions inside cats.effect.IO" in {
    recoverToSucceededIf[ClientException] {
      execute { s =>
        "match test return p.name"
          .query[String]
          .single(s)
      }.unsafeToFuture()
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
