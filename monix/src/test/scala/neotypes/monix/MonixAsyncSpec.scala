package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import neotypes.monix.implicits._
import neotypes.BaseIntegrationSpec
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.AsyncFlatSpec

class MonixAsyncSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "work with Task" in {
    val s = driver.session().asScala[Task]

    "match (p:Person {name: 'Charlize Theron'}) return p.name"
      .query[String]
      .single(s)
      .runToFuture
      .map {
        name => assert(name == "Charlize Theron")
      }

    recoverToSucceededIf[ClientException] {
      "match test return p.name"
        .query[String]
        .single(s)
        .runToFuture
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
