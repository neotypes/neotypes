package neotypes.fs2

import cats.effect.IO
import neotypes.BaseIntegrationSpec
import neotypes.cats.implicits._
import neotypes.fs2.implicits._
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.session._
import neotypes.implicits.syntax.string._

class Fs2StreamSpec extends BaseIntegrationSpec {
  it should "work with fs2 using cats.effect.IO" in {
    val s = driver.session().asScala[IO]

    "match (p:Person) return p.name"
      .query[Int]
      .stream[Fs2IoStream](s)
      .compile
      .toList
      .unsafeToFuture()
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  override val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
