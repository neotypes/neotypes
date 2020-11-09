package neotypes.cats.data

import neotypes.{CleaningIntegrationSpec, FutureTestkit}
import neotypes.generic.auto.all._
import neotypes.cats.data.implicits._
import neotypes.exceptions.IncoercibleException
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import neotypes.implicits.syntax.string._

import cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}
import _root_.cats.instances.string._ // Brings the implicit Order[String] instance into the scope.
import _root_.cats.instances.int._ // Brings the implicit Order[Int] instance into the scope.

import scala.concurrent.Future

final class CatsDataSpec extends CleaningIntegrationSpec[Future](FutureTestkit) {
  import CatsDataSpec._

  it should "work with Chain" in executeAsFuture { s =>
    val messages: Messages = Chain("a", "b")

    for {
      _ <- c"CREATE (chat: Chat { user1: ${"Balmung"}, user2: ${"Luis"}, messages: ${messages} })".query[Unit].execute(s)
      r1 <- "MATCH (chat: Chat) RETURN chat.messages".query[Messages].single(s)
      r2 <- "MATCH (chat: Chat) RETURN chat".query[Chat].single(s)
    } yield {
      assert(r1 == messages)
      assert(r2 == Chat("Balmung", "Luis", messages))
    }
  }

  it should "work with Const" in executeAsFuture { s =>
    val name: Name = Const("Balmung")

    for {
      _ <- c"CREATE (user: User { name: ${name} })".query[Unit].execute(s)
      r1 <- "MATCH (user: User) RETURN user.name".query[Name].single(s)
      r2 <- "MATCH (user: User) RETURN user".query[User].single(s)
    } yield {
      assert(r1 == name)
      assert(r2 == User(name))
    }
  }

  it should "work with NonEmptyChain" in executeAsFuture { s =>
    val errors: Errors = NonEmptyChain("a", "b")

    for {
      _ <- c"CREATE (stackTrace: StackTrace { line: 1, errors: ${errors} })".query[Unit].execute(s)
      r1 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace.errors".query[Errors].single(s)
      r2 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(s)
    } yield {
      assert(r1 == errors)
      assert(r2 == StackTrace(1, errors))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyChain" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (stackTrace: StackTrace { line: 1, errors: [] })".query[Unit].execute(s)
        stackTrace <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(s)
      } yield stackTrace
    }
  }

  it should "work with NonEmptyList" in executeAsFuture { s =>
    val items: Items = NonEmptyList.of("a", "b")

    for {
      _ <- c"CREATE (player: Player { name: ${"Luis"}, items: ${items} })".query[Unit].execute(s)
      r1 <- "MATCH (player: Player) RETURN player.items".query[Items].single(s)
      r2 <- "MATCH (player: Player) RETURN player".query[Player].single(s)
    } yield {
      assert(r1 == items)
      assert(r2 == Player("Luis", items))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyList" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (player: Player { name: \"Luis\", items: [] })".query[Unit].execute(s)
        player <- "MATCH (player: Player) RETURN player".query[Player].single(s)
      } yield player
    }
  }

  it should "work with NonEmptyMap" in executeAsFuture { s =>
    val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)

    for {
      _ <- c"CREATE (config: Config ${properties})".query[Unit].execute(s)
      r1 <- "MATCH (config: Config) RETURN config { .* }".query[Properties].single(s)
      r2 <- "MATCH (config: Config) RETURN \"dev\" AS env, config { .* } AS properties".query[Config].single(s)
    } yield {
      assert(r1 == properties)
      assert(r2 == Config("dev", properties))
    }
  }

  it should "fail if retrieving an empty map as a NonEmptyMap" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        properties <- "RETURN {}".query[Properties].single(s)
      } yield properties
    }
  }

  it should "work with NonEmptySet" in executeAsFuture { s =>
    val numbers: Numbers = NonEmptySet.of(1, 3, 5)

    for {
      _ <- c"CREATE (set: Set { name: ${"favourites"}, numbers: ${numbers} })".query[Unit].execute(s)
      r1 <- "MATCH (set: Set) RETURN set.numbers".query[Numbers].single(s)
      r2 <- "MATCH (set: Set) RETURN set".query[MySet].single(s)
    } yield {
      assert(r1 == numbers)
      assert(r2 == MySet("favourites", numbers))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptySet" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (set: Set { name: \"favourites\", numbers: [] })".query[Unit].execute(s)
        set <- "MATCH (set: Set) RETURN set".query[MySet].single(s)
      } yield set
    }
  }

  it should "work with NonEmptyVector" in executeAsFuture { s =>
    val groceries: Groceries = NonEmptyVector.of("a", "b")

    for {
      _ <- c"CREATE (purchase: Purchase { total: 12.5, groceries: ${groceries} })".query[Unit].execute(s)
      r1 <- "MATCH (purchase: Purchase) RETURN purchase.groceries".query[Groceries].single(s)
      r2 <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(s)
    } yield {
      assert(r1 == groceries)
      assert(r2 == Purchase(12.5d, groceries))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyVector" in executeAsFuture { s =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (purchase: Purchase { total: 12.5, groceries: [] })".query[Unit].execute(s)
        purchase <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(s)
      } yield purchase
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
