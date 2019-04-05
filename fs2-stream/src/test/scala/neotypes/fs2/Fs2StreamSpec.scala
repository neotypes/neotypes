package neotypes.fs2

import cats.effect.IO
import neotypes.BaseIntegrationSpec
import neotypes.cats.implicits._
import neotypes.fs2.implicits._
import neotypes.implicits._
import org.scalatest.AsyncFlatSpec

class Fs2StreamSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  type Fs2Stream[T] = fs2.Stream[IO, T]

  it should "work with fs2 using cats.effect.IO" in {
    val s = driver.session().asScala[IO]

    "match (p:Person) return p.name"
      .query[Int]
      .stream[Fs2Stream, IO](s)
      .compile
      .toList
      .unsafeToFuture
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  override val initQuery: String =
    (0 to 10).map(n => s"CREATE (:Person {name: $n})").mkString("\n")
}
