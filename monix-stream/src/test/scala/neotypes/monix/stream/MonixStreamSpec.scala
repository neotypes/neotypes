package neotypes.monix.stream

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.BaseIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import neotypes.monix.implicits._
import neotypes.monix.stream.implicits._

class MonixStreamSpec extends BaseIntegrationSpec {
  it should "work with monix.reactive.Observable" in {
    val s = driver.session().asScala[Task]

    "match (p:Person) return p.name"
      .query[Int]
      .stream[MonixStream](s)
      .toListL
      .runToFuture
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  override val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
