package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class QueryExecutionSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "retrieve multiple results as a List" in {
    val s = driver.session().asScala[Future]

    "match (p:Person) return p.name"
      .query[Int]
      .list(s)
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  it should "retrieve multiple results as a Set" in {
    val s = driver.session().asScala[Future]

    "match (p:Person) return p.name"
      .query[Int]
      .set(s)
      .map {
        names => assert(names == (0 to 10).toSet)
      }
  }

  it should "retrieve multiple results as a Vector" in {
    val s = driver.session().asScala[Future]

    "match (p:Person) return p.name"
      .query[Int]
      .vector(s)
      .map {
        names => assert(names == (0 to 10).toVector)
      }
  }

  override def initQuery: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n")
}
