package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream._
import neotypes.Async._
import neotypes.BaseIntegrationSpec
import neotypes.akkastreams.AkkaStream._
import neotypes.implicits.{StringExt, _}
import org.scalatest.AsyncFlatSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AkkaStreamSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  it should "work with IO" in {
    val s = driver.session().asScala[Future]
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()

    s.transact { tx =>
      "match (p:Person) return p.name"
        .query[Int]
        .stream[AkkaStream.Stream, Future](tx)
        .runForeach(i ⇒ println(i))(materializer)
    }.map { _ =>
      assert(1 == 1)
    }
  }

  override val initQuery: String =
    (0 to 50).map(n => s"CREATE (:Person {name: $n})").mkString("\n") //+ "\n CREATE (:Person {name: 'asd'})"
}
