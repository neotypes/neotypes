package neotypes

import neotypes.exceptions.UncoercibleException
import neotypes.implicits._

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Interval
import org.scalatest.FreeSpec
import org.scalatest.{AsyncFlatSpec, FutureOutcome}

import scala.concurrent.Future

class RefinedSpec extends AsyncFlatSpec with BaseIntegrationSpec {
  import RefinedSpec.{Level, User}

  // Clean the graph after each test.
  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    val f = for {
      r <- super.withFixture(test).toFuture
      _ <- driver.asScala[Future].writeSession { s =>
             "MATCH (n) DETACH DELETE n".query[Unit].execute(s)
           }
    } yield r
    new FutureOutcome(f)
  }

  override val initQuery: String = RefinedSpec.INIT_QUERY

  it should "insert and retrieve one refined value" in {
    val s = driver.session().asScala[Future]

    val L1: Level = 1

    for {
      _ <- c"CREATE (level: Level { value: ${L1} })".query[Unit].execute(s)
      value <- "MATCH (level: Level) RETURN level.value".query[Level].single(s)
    } yield assert(value == L1)
  }

  it should "insert and retrieve multiple refined values" in {
    val s = driver.session().asScala[Future]

    val L1: Level = 1
    val L2: Level = 2

    for {
      _ <- c"CREATE (level: Level { value: ${L1} })".query[Unit].execute(s)
      _ <- c"CREATE (level: Level { value: ${L2} })".query[Unit].execute(s)
      values <- "MATCH (level: Level) RETURN level.value".query[Level].list(s)
    } yield assert(values == List(L1, L2))
  }

  it should "insert and retrieve wrapped refined values" in {
    val s = driver.session().asScala[Future]

    val L1: Level = 1
    val L2: Level = 2
    val levels = Option(List(L1, L2))

    for {
      _ <- c"CREATE (levels: Levels { values: ${levels} })".query[Unit].execute(s)
      values <- "MATCH (levels: Levels) RETURN levels.values".query[Option[List[Level]]].single(s)
    } yield assert(values == levels)
  }

  it should "insert and retrieve refined values inside a case class" in {
    val s = driver.session().asScala[Future]

    for {
      _ <- "CREATE (user: User { name: \"Balmung\",  level: 99 })".query[Unit].execute(s)
      user <- "MATCH (user: User { name: \"Balmung\" }) RETURN user".query[User].single(s)
    } yield assert(user == User(name = "Balmung", level = 99))
  }

  it should "fail if a single value does not satisfy the refinement condition" in {
    val s = driver.session().asScala[Future]

    recoverToSucceededIf[UncoercibleException] {
      for {
        _ <- "CREATE (level: Level { value: -1 })".query[Unit].execute(s)
        level <- "MATCH (level: Level) RETURN level".query[Level].single(s)
      } yield level
    }
  }

  it should "fail if at least one of multiple values does not satisfy the refinement condition" in {
    val s = driver.session().asScala[Future]

    recoverToSucceededIf[UncoercibleException] {
      for {
        _ <- "CREATE (level: Level { value: 3 })".query[Unit].execute(s)
        _ <- "CREATE (level: Level { value: -1 })".query[Unit].execute(s)
        _ <- "CREATE (level: Level { value: 5 })".query[Unit].execute(s)
        levels <- "MATCH (level: Level) RETURN level".query[Level].list(s)
      } yield levels
    }
  }

  it should "fail if at least one wrapped value does not satisfy the refinement condition" in {
    val s = driver.session().asScala[Future]

    recoverToSucceededIf[UncoercibleException] {
      for {
        _ <- "CREATE (levels: Levels { values: [3, -1, 5] })".query[Unit].execute(s)
        levels <- "MATCH (levels: Levels) RETURN levels.values".query[Option[List[Level]]].single(s)
      } yield levels
    }
  }

  it should "fail if at least one value inside a case class does not satisfy the refinement condition" in {
    val s = driver.session().asScala[Future]

    recoverToSucceededIf[UncoercibleException] {
      for {
        _ <- "CREATE (user: User { name: \"???\", level: -1 })".query[Unit].execute(s)
        user <- "MATCH (user: User { name: \"???\" }) RETURN user".query[User].single(s)
      } yield user
    }
  }
}

object RefinedSpec {
  type Level = Int Refined Interval.Closed[W.`1`.T, W.`99`.T]

  final case class User(name: String, level: Level)

  val INIT_QUERY: String = null
}
