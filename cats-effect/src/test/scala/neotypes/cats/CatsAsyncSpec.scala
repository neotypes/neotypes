package neotypes.cats

import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException

class CatsAsyncSpec extends BaseIntegrationSpec {
  it should "work with IO" in {
    val s = driver.session().asScala[IO]

    """match (p:Person {name: "Charlize Theron"}) return p.name"""
      .query[String]
      .single(s)
      .unsafeToFuture()
      .map {
        name => assert(name == "Charlize Theron")
      }

    recoverToSucceededIf[ClientException] {
      "match test return p.name"
        .query[String]
        .single(s)
        .unsafeToFuture()
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
