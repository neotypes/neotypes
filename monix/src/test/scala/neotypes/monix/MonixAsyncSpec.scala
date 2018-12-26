package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.monix.implicits._
import neotypes.implicits._
import neotypes.{BaseIntegrationSpec, BasicSessionSpec}
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.FlatSpec

import scala.concurrent.duration._

class MonixAsyncSpec extends FlatSpec with BaseIntegrationSpec {
  it should "work with Task" in {
    val s = driver.session().asScala[Task]

    val string = "match (p:Person {name: 'Charlize Theron'}) return p.name".query[String].single(s)
      .runSyncUnsafe(5 seconds)
    assert(string == "Charlize Theron")

    assertThrows[ClientException] {
      "match test return p.name".query[String].single(s).runSyncUnsafe(5 seconds)
    }
  }

  override val initQuery: String = BasicSessionSpec.INIT_QUERY
}
