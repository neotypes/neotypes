package neotypes.cats

import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.implicits._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.AsyncFlatSpec

class CatsAsyncSpec extends AsyncFlatSpec with BaseIntegrationSpec {
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
