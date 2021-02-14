package neotypes.cats.data

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.generic.auto._
import neotypes.cats.data.implicits._
import neotypes.exceptions.IncoercibleException
import neotypes.implicits.syntax.cypher._
import neotypes.implicits.syntax.string._
import cats.data.{Chain, Const, NonEmptyChain, NonEmptyList, NonEmptyMap, NonEmptySet, NonEmptyVector}
import cats.instances.string._
import cats.instances.int._
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.Future

final class CatsDataSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] {
  import CatsDataSpec._

  "querying" should {
    "work with Chain" in executeAsFuture { s =>
      val messages: Messages = Chain("a", "b")

      for {
        _ <- c"CREATE (chat: Chat { user1: ${"Balmung"}, user2: ${"Luis"}, messages: ${messages} })".query[Unit].execute(s)
        r1 <- "MATCH (chat: Chat) RETURN chat.messages".query[Messages].single(s)
        r2 <- "MATCH (chat: Chat) RETURN chat".query[Chat].single(s)
      } yield {
        r1 shouldBe messages
        r2 shouldBe Chat("Balmung", "Luis", messages)
      }
    }
    "work with Const" in executeAsFuture { s =>
      val name: Name = Const("Balmung")

      for {
        _ <- c"CREATE (user: User { name: ${name} })".query[Unit].execute(s)
        r1 <- "MATCH (user: User) RETURN user.name".query[Name].single(s)
        r2 <- "MATCH (user: User) RETURN user".query[User].single(s)
      } yield {
        r1 shouldBe name
        r2 shouldBe User(name)
      }
    }
    "work with NonEmptyChain" in executeAsFuture { s =>
      val errors: Errors = NonEmptyChain("a", "b")

      for {
        _ <- c"CREATE (stackTrace: StackTrace { line: 1, errors: ${errors} })".query[Unit].execute(s)
        r1 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace.errors".query[Errors].single(s)
        r2 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(s)
      } yield {
        r1 shouldBe errors
        r2 shouldBe StackTrace(1, errors)
      }
    }
    "fail if retrieving an empty list as a NonEmptyChain" in executeAsFuture { s =>
      recoverToSucceededIf[IncoercibleException] {
        for {
          _ <- "CREATE (stackTrace: StackTrace { line: 1, errors: [] })".query[Unit].execute(s)
          stackTrace <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(s)
        } yield stackTrace
      }
    }
    "work with NonEmptyList" in executeAsFuture { s =>
      val items: Items = NonEmptyList.of("a", "b")

      for {
        _ <- c"CREATE (player: Player { name: ${"Luis"}, items: ${items} })".query[Unit].execute(s)
        r1 <- "MATCH (player: Player) RETURN player.items".query[Items].single(s)
        r2 <- "MATCH (player: Player) RETURN player".query[Player].single(s)
      } yield {
        r1 shouldBe items
        r2 shouldBe Player("Luis", items)
      }
    }
    "fail if retrieving an empty list as a NonEmptyList" in executeAsFuture { s =>
      recoverToSucceededIf[IncoercibleException] {
        for {
          _ <- "CREATE (player: Player { name: \"Luis\", items: [] })".query[Unit].execute(s)
          player <- "MATCH (player: Player) RETURN player".query[Player].single(s)
        } yield player
      }
    }
    "work with NonEmptyMap" in executeAsFuture { s =>
      val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)

      for {
        _ <- c"CREATE (config: Config ${properties})".query[Unit].execute(s)
        r1 <- "MATCH (config: Config) RETURN config { .* }".query[Properties].single(s)
        r2 <- "MATCH (config: Config) RETURN \"dev\" AS env, config { .* } AS properties".query[Config].single(s)
      } yield {
        r1 shouldBe properties
        r2 shouldBe Config("dev", properties)
      }
    }
    "fail if retrieving an empty map as a NonEmptyMap" in executeAsFuture { s =>
      recoverToSucceededIf[IncoercibleException] {
        for {
          properties <- "RETURN {}".query[Properties].single(s)
        } yield properties
      }
    }
    "work with NonEmptySet" in executeAsFuture { s =>
      val numbers: Numbers = NonEmptySet.of(1, 3, 5)

      for {
        _ <- c"CREATE (set: Set { name: ${"favourites"}, numbers: ${numbers} })".query[Unit].execute(s)
        r1 <- "MATCH (set: Set) RETURN set.numbers".query[Numbers].single(s)
        r2 <- "MATCH (set: Set) RETURN set".query[MySet].single(s)
      } yield {
        r1 shouldBe numbers
        r2 shouldBe MySet("favourites", numbers)
      }
    }
    "fail if retrieving an empty list as a NonEmptySet" in executeAsFuture { s =>
      recoverToSucceededIf[IncoercibleException] {
        for {
          _ <- "CREATE (set: Set { name: \"favourites\", numbers: [] })".query[Unit].execute(s)
          set <- "MATCH (set: Set) RETURN set".query[MySet].single(s)
        } yield set
      }
    }
    "work with NonEmptyVector" in executeAsFuture { s =>
      val groceries: Groceries = NonEmptyVector.of("a", "b")

      for {
        _ <- c"CREATE (purchase: Purchase { total: 12.5, groceries: ${groceries} })".query[Unit].execute(s)
        r1 <- "MATCH (purchase: Purchase) RETURN purchase.groceries".query[Groceries].single(s)
        r2 <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(s)
      } yield {
        r1 shouldBe groceries
        r2 shouldBe Purchase(12.5d, groceries)
      }
    }
    "fail if retrieving an empty list as a NonEmptyVector" in executeAsFuture { s =>
      recoverToSucceededIf[IncoercibleException] {
        for {
          _ <- "CREATE (purchase: Purchase { total: 12.5, groceries: [] })".query[Unit].execute(s)
          purchase <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(s)
        } yield purchase
      }
    }
  }
}

object CatsDataSpec {
  type Messages = Chain[String]
  final case class Chat(user1: String, user2: String, messages: Messages)

  type Name = Const[String, Int]
  final case class User(name: Name)

  type Errors = NonEmptyChain[String]
  final case class StackTrace(line: Int, errors: Errors)

  type Items = NonEmptyList[String]
  final case class Player(name: String, items: Items)

  type Properties = NonEmptyMap[String, Boolean]
  final case class Config(env: String, properties: Properties)

  type Numbers = NonEmptySet[Int]
  final case class MySet(name: String, numbers: Numbers)

  type Groceries = NonEmptyVector[String]
  final case class Purchase(total: Double, groceries: Groceries)
}
