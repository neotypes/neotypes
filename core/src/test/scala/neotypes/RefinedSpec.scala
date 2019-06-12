package neotypes

import neotypes.implicits._

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Interval
import org.scalatest.FreeSpec
import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future

class RefinedSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  type Level = Int Refined Interval.Closed[W.`1`.T, W.`99`.T]

  override val initQuery: String = "CREATE (level: Level { value: 1 })"

  it should "work with Refined types" in {
    val s = driver.session().asScala[Future]

    val L2: Level = 2

    for {
      _ <- c"CREATE (level: Level { value: ${L2} })".query[Unit].execute(s)
      values <- c"MATCH (level: Level) RETURN level.value".query[Level].list(s)
    } yield assert(values == List(1: Level, L2))
  }
}
