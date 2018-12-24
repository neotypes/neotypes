package neotypes.cats

import cats.effect.IO
import neotypes.cats.implicits._
import neotypes.implicits._
import neotypes.{BaseIntegrationSpec, BasicSessionSpec}
import org.scalatest.FlatSpec

class CatsAsyncSpec extends FlatSpec with BaseIntegrationSpec {
  it should "test" in {
    val s = driver.session().asScala[IO]

    for {
      string <- "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
    } yield {
      assert(string == "Charlize Theron")
    }
  }

  override val initQuery: String = BasicSessionSpec.INIT_QUERY
}
