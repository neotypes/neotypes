package neotypes.cats.data

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.cats.data.implicits._
import neotypes.exceptions.IncoercibleException
import neotypes.generic.auto._
import neotypes.implicits.syntax.all._

import cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}

import scala.concurrent.Future

final class CatsDataSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] {
  import CatsDataSpec._

  it should "work with Chain" in executeAsFuture { d =>
    val messages: Messages = Chain("a", "b")

    for {
      _ <- c"CREATE (chat: Chat { user1: 'Balmung', user2: 'Luis', messages: ${messages} })".query[Unit].execute(d)
      r1 <- "MATCH (chat: Chat) RETURN chat.messages".query[Messages].single(d)
      r2 <- "MATCH (chat: Chat) RETURN chat".query[Chat].single(d)
    } yield {
      assert(r1 == messages)
      assert(r2 == Chat("Balmung", "Luis", messages))
    }
  }

  it should "work with Const" in executeAsFuture { d =>
    val name: Name = Const("Balmung")

    for {
      _ <- c"CREATE (user: User { name: ${name} })".query[Unit].execute(d)
      r1 <- "MATCH (user: User) RETURN user.name".query[Name].single(d)
      r2 <- "MATCH (user: User) RETURN user".query[User].single(d)
    } yield {
      assert(r1 == name)
      assert(r2 == User(name))
    }
  }

  it should "work with NonEmptyChain" in executeAsFuture { d =>
    val errors: Errors = NonEmptyChain("a", "b")

    for {
      _ <- c"CREATE (stackTrace: StackTrace { line: 1, errors: ${errors} })".query[Unit].execute(d)
      r1 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace.errors".query[Errors].single(d)
      r2 <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(d)
    } yield {
      assert(r1 == errors)
      assert(r2 == StackTrace(1, errors))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyChain" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (stackTrace: StackTrace { line: 1, errors: [] })".query[Unit].execute(d)
        stackTrace <- "MATCH (stackTrace: StackTrace) RETURN stackTrace".query[StackTrace].single(d)
      } yield stackTrace
    }
  }

  it should "work with NonEmptyList" in executeAsFuture { d =>
    val items: Items = NonEmptyList.of("a", "b")

    for {
      _ <- c"CREATE (player: Player { name: 'Luis', items: ${items} })".query[Unit].execute(d)
      r1 <- "MATCH (player: Player) RETURN player.items".query[Items].single(d)
      r2 <- "MATCH (player: Player) RETURN player".query[Player].single(d)
    } yield {
      assert(r1 == items)
      assert(r2 == Player("Luis", items))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyList" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (player: Player { name: 'Luis', items: [] })".query[Unit].execute(d)
        player <- "MATCH (player: Player) RETURN player".query[Player].single(d)
      } yield player
    }
  }

  it should "work with NonEmptyMap" in executeAsFuture { d =>
    val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)

    for {
      _ <- c"CREATE (config: Config ${properties})".query[Unit].execute(d)
      r1 <- "MATCH (config: Config) RETURN config { .* }".query[Properties].single(d)
      r2 <- "MATCH (config: Config) RETURN 'dev' AS env, config { .* } AS properties".query[Config].single(d)
    } yield {
      assert(r1 == properties)
      assert(r2 == Config("dev", properties))
    }
  }

  it should "fail if retrieving an empty map as a NonEmptyMap" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        properties <- "RETURN {}".query[Properties].single(d)
      } yield properties
    }
  }

  it should "work with NonEmptySet" in executeAsFuture { d =>
    val numbers: Numbers = NonEmptySet.of(1, 3, 5)

    for {
      _ <- c"CREATE (set: Set { name: 'favorites', numbers: ${numbers} })".query[Unit].execute(d)
      r1 <- "MATCH (set: Set) RETURN set.numbers".query[Numbers].single(d)
      r2 <- "MATCH (set: Set) RETURN set".query[MySet].single(d)
    } yield {
      assert(r1 == numbers)
      assert(r2 == MySet("favorites", numbers))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptySet" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (set: Set { name: 'favorites', numbers: [] })".query[Unit].execute(d)
        set <- "MATCH (set: Set) RETURN set".query[MySet].single(d)
      } yield set
    }
  }

  it should "work with NonEmptyVector" in executeAsFuture { d =>
    val groceries: Groceries = NonEmptyVector.of("a", "b")

    for {
      _ <- c"CREATE (purchase: Purchase { total: 12.5, groceries: ${groceries} })".query[Unit].execute(d)
      r1 <- "MATCH (purchase: Purchase) RETURN purchase.groceries".query[Groceries].single(d)
      r2 <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(d)
    } yield {
      assert(r1 == groceries)
      assert(r2 == Purchase(12.5d, groceries))
    }
  }

  it should "fail if retrieving an empty list as a NonEmptyVector" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      for {
        _ <- "CREATE (purchase: Purchase { total: 12.5, groceries: [] })".query[Unit].execute(d)
        purchase <- "MATCH (purchase: Purchase) RETURN purchase".query[Purchase].single(d)
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
