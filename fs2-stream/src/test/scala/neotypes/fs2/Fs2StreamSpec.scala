package neotypes.fs2

import cats.effect.IO
import neotypes.BaseIntegrationSpec
import neotypes.cats.effect.implicits._
import neotypes.fs2.implicits._
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._

class Fs2StreamSpec extends BaseIntegrationSpec[IO] {
  it should "work with fs2.Stream using cats.effect.IO" in execute { s =>
    "match (p:Person) return p.name"
      .query[Int]
      .stream[Fs2IoStream](s)
      .compile
      .toList
  }.unsafeToFuture().map {
    names => assert(names == (0 to 10).toList)
  }

  override val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
