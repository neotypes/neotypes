package neotypes

import neotypes.Async._
import neotypes.implicits._
import org.neo4j.driver.v1.types.Node
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class ParameterSessionSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "convert parameters" in {
    val s = driver.session().asScala[Future]

    val name = "test"
    val born = 123
    val lastName = None
    val middleName = Some("test2")

    for {
      _ <- c"create (p:Person {name: $name, born: $born, lastName: $lastName, middleName: $middleName})".query[Unit].execute(s)
      res <- "match (p:Person) return p limit 1".query[Node].single(s)
    } yield {
      assert(res.get("name").asString() == "test")
      assert(res.get("born").asInt() == 123)
      assert(res.get("lastName").isNull)
      assert(res.get("middleName").asString() == "test2")
    }
  }

  override val initQuery: String = null
}
