package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Sink
import neotypes.Async._
import neotypes.BaseIntegrationSpec
import neotypes.akkastreams.implicits.{AkkaStream, _}
import neotypes.implicits._
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class AkkaStreamSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "work with Akka streams" in {
    val s = driver.session().asScala[Future]
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()

    "match (p:Person) return p.name"
      .query[Int]
      .stream[AkkaStream, Future](s)
      .runWith(Sink.seq[Int]).map {
        names => assert(names == (0 to 10))
      }
  }

  override val initQuery: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n") //+ "\n CREATE (:Person {name: 'asd'})"
}
