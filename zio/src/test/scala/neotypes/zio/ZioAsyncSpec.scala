package neotypes.zio

import zio.Task
import zio.DefaultRuntime
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import neotypes.zio.implicits._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException

class ZioAsyncSpec extends BaseIntegrationSpec {
  it should "work with ZIO" in {
    val runtime = new DefaultRuntime {}

    val s = driver.session().asScala[Task]

    val program =
      "match (p:Person {name: 'Charlize Theron'}) return p.name"
        .query[String]
        .single(s)

    runtime
      .unsafeRunToFuture(program)
      .map { name =>
        assert(name == "Charlize Theron")
      }

    recoverToSucceededIf[ClientException] {
      runtime.unsafeRunToFuture {
        "match test return p.name"
          .query[String]
          .single(s)
      }
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
