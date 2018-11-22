package neotypes

import neotypes.Async._
import neotypes.implicits._
import shapeless._
import scala.concurrent.Future

class ParameterSessionSpec extends BaseIntegrationSpec {
  it should "convert parameters" in {
    val s = new Session[Future](driver.session())

    for {
      _ <- "create (p:Person {name: $name})".query[Unit](Map("name" -> "test")).execute(s)
      res <- "match (p:Person) return p.name limit 1".query[String]().single(s)
    } yield {
      assert(res == "test")
    }
  }
}
