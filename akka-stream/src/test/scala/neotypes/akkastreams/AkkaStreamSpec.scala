package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Sink
import neotypes.BaseIntegrationSpec
import neotypes.akkastreams.implicits._
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class AkkaStreamSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "work with Akka streams" in {
    val s = driver.session().asScala[Future]
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()

    "match (p:Person) return p.name"
      .query[Int]
      .stream[AkkaStream](s)
      .runWith(Sink.seq[Int])
      .map {
        names => assert(names == (0 to 10))
      }
  }

  override val initQuery: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n") //+ "\n CREATE (:Person {name: 'asd'})"
}
