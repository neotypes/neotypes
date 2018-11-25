package neotypes

import neotypes.Async._
import neotypes.implicits._
import shapeless._
import scala.concurrent.Future

class ParameterSessionSpec extends BaseIntegrationSpec {
  it should "convert parameters" in {
    val s = driver.session().asScala[Future]

    for {
      _ <- "create (p:Person {name: $name, born: $born})".query[Unit].withParams(Map("name" -> "test", "born" -> 123)).execute(s)
      res <- "match (p:Person) return p.name, p.born limit 1".query[String :: Int :: HNil].single(s)
    } yield {
      val name :: born :: HNil = res
      assert(name == "test")
      assert(born == 123)
    }
  }
}
