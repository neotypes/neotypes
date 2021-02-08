package neotypes.refined

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.generic.auto._
import neotypes.exceptions.IncoercibleException
import neotypes.implicits.syntax.cypher._
import neotypes.implicits.syntax.string._
import neotypes.refined.implicits._

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Interval

import scala.concurrent.Future

final class RefinedSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] {
  import RefinedSpec.{Level, User}

  it should "insert and retrieve one refined value" in executeAsFuture { s =>
    val L1: Level = 1

    for {
      _ <- c"CREATE (level: Level { value: ${L1} })".query[Unit].execute(s)
      level <- "MATCH (level: Level) RETURN level.value".query[Level].single(s)
    } yield assert(level == L1)
  }

  it should "insert and retrieve multiple refined values" in executeAsFuture { s =>
    val L1: Level = 1
    val L2: Level = 2

    for {
      _ <- c"CREATE (level: Level { value: ${L1} })".query[Unit].execute(s)
      _ <- c"CREATE (level: Level { value: ${L2} })".query[Unit].execute(s)
      levels <- "MATCH (level: Level) RETURN level.value ORDER BY level.value ASC".query[Level].list(s)
    } yield assert(levels == List(L1, L2))
  }

  it should "insert and retrieve wrapped refined values" in executeAsFuture { s =>
    val L1: Level = 1
    val L2: Level = 2
    val levels = List(Option(L1), Option(L2))

    for {
      _ <- c"CREATE (levels: Levels { values: ${levels} })".query[Unit].execute(s)
      levels <- "MATCH (levels: Levels) UNWIND levels.values AS level RETURN level".query[Option[Level]].list(s)
    } yield assert(levels == levels)
  }

  it should "retrieve refined values inside a case class" in executeAsFuture { s =>
    for {
      _ <- "CREATE (user: User { name: \"Balmung\",  level: 99 })".query[Unit].execute(s)
      user <- "MATCH (user: User { name: \"Balmung\" }) RETURN user".query[User].single(s)
    } yield assert(user == User(name = "Balmung", level = 99))
  }

  it should "fail if a single value does not satisfy the refinement condition" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (level: Level { value: -1 })".query[Unit].execute(s)
        level <- "MATCH (level: Level) RETURN level".query[Level].single(s)
      } yield level
    }
  }

  it should "fail if at least one of multiple values does not satisfy the refinement condition" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (level: Level { value: 3 })".query[Unit].execute(s)
        _ <- "CREATE (level: Level { value: -1 })".query[Unit].execute(s)
        _ <- "CREATE (level: Level { value: 5 })".query[Unit].execute(s)
        levels <- "MATCH (level: Level) RETURN level".query[Level].list(s)
      } yield levels
    }
  }

  it should "fail if at least one wrapped value does not satisfy the refinement condition" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (levels: Levels { values: [3, -1, 5] })".query[Unit].execute(s)
        levels <- "MATCH (levels: Levels) UNWIND levels.values AS level RETURN level".query[Option[Level]].list(s)
      } yield levels
    }
  }

  it should "fail if at least one value inside a case class does not satisfy the refinement condition" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
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
}
