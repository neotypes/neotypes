package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import neotypes.monix.implicits._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException

import scala.concurrent.ExecutionContext

class MonixAsyncSpec extends BaseIntegrationSpec[Task] { self =>
  implicit val scheduler: Scheduler = Scheduler(self.executionContext)

  it should "work with monix.eval.Task" in execute { s =>
    "match (p:Person {name: 'Charlize Theron'}) return p.name"
      .query[String]
      .single(s)
  }.runToFuture.map {
    name => assert(name == "Charlize Theron")
  }

  it should "catch exceptions inside monix.eval.Task" in {
    recoverToSucceededIf[ClientException] {
      execute { s =>
        "match test return p.name"
          .query[String]
          .single(s)
      }.runToFuture
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
