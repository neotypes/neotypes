package neotypes.monix.stream

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import neotypes.BaseIntegrationSpec
import neotypes.monix.implicits._
import neotypes.monix.stream.implicits._
import neotypes.implicits._
import org.scalatest.AsyncFlatSpec

class MonixStreamSpec extends AsyncFlatSpec with BaseIntegrationSpec {
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

  override val initQuery: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n")
}
