package neotypes.cats

import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.implicits._
import neotypes.{BaseIntegrationSpec, BasicSessionSpec}
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.FlatSpec

class CatsAsyncSpec extends FlatSpec with BaseIntegrationSpec {
  it should "work with IO" in {
    val s = driver.session().asScala[IO]

    val string = "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s).unsafeRunSync()
    assert(string == "Charlize Theron")

    assertThrows[ClientException] {
      "match test return p.name".query[String].single(s).unsafeRunSync()
    }
  }

  override val initQuery: String = BasicSessionSpec.INIT_QUERY
}
