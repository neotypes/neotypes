package neotypes.zio

import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import neotypes.zio.implicits._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException

import scala.concurrent.ExecutionContext

class ZioAsyncSpec extends BaseIntegrationSpec[Task] { self =>
  val runtime = new DefaultRuntime { override val Platform = PlatformLive.fromExecutionContext(self.executionContext) }

  it should "work with zio.Task" in {
    val runtime = new DefaultRuntime {}

    val program = execute { s =>
      "match (p:Person {name: 'Charlize Theron'}) return p.name"
        .query[String]
        .single(s)
    }

    runtime
      .unsafeRunToFuture(program)
      .map { name =>
        assert(name == "Charlize Theron")
      }
  }

  it should "catch exceptions inside zio.Task" in {
    val runtime = new DefaultRuntime {}

    recoverToSucceededIf[ClientException] {
      val program = execute { s =>
        "match test return p.name"
          .query[String]
          .single(s)
      }

      runtime.unsafeRunToFuture(program)
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
