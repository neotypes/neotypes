package neotypes.cats.data

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.cats.data.mappers._
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.IncoercibleException
import neotypes.syntax.all._

import cats.data.{
  Chain,
  Const,
  NonEmptyChain,
  NonEmptyList,
  NonEmptyMap,
  NonEmptySet,
  NonEmptyVector
}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

final class CatsDataSpec extends AsyncDriverProvider[Future](FutureTestkit) with CleaningIntegrationSpec[Future] with Matchers {
  import CatsDataSpec._
  import ResultMapper._

  behavior of s"${driverName} with cats.data types"

  it should "work with Const" in executeAsFuture { driver =>
    val name: Name = Const("Balmung")
    val mapper: ResultMapper[Name] = const(string)

    for {
      _ <- c"CREATE (: User { name: ${name} })".execute.void(driver)
      r <- "MATCH (user: User) RETURN user.name".query(mapper).single(driver)
    } yield {
      r shouldBe name
    }
  }

  it should "work with Chain" in executeAsFuture { d =>
    val messages: Messages = Chain("a", "b")
    val mapper: ResultMapper[Messages] = chain(string)

    for {
      _ <- c"CREATE (: Chat { user1: 'Balmung', user2: 'Luis', messages: ${messages} })".execute.void(d)
      r <- "MATCH (chat: Chat) RETURN chat.messages".query(mapper).single(d)
    } yield {
      r shouldBe messages
    }
  }

  it should "work with NonEmptyChain" in executeAsFuture { d =>
    val errors: Errors = NonEmptyChain("a", "b")
    val mapper: ResultMapper[Errors] = nonEmptyChain(string)

    // Success.
    for {
      _ <- c"CREATE (: StackTrace { line: 1, errors: ${errors} })".execute.void(d)
      r <- "MATCH (stackTrace: StackTrace) RETURN stackTrace.errors".query(mapper).single(d)
    } yield {
      r shouldBe errors
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN []".query(mapper).single(d)
    }
  }

  it should "work with NonEmptyList" in executeAsFuture { driver =>
    val items: Items = NonEmptyList.of("a", "b")
    val mapper: ResultMapper[Items] = nonEmptyList(string)

    // Success.
    for {
      _ <- c"CREATE (: Player { name: 'Luis', items: ${items} })".execute.void(driver)
      r <- "MATCH (player: Player) RETURN player.items".query(mapper).single(driver)
    } yield {
      r shouldBe items
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN []".query(mapper).single(driver)
    }
  }

  it should "work with NonEmptyVector" in executeAsFuture { driver =>
    val groceries: Groceries = NonEmptyVector.of("a", "b")
    val mapper: ResultMapper[Groceries] = nonEmptyVector(string)

    // Success.
    for {
      _ <- c"CREATE (: Purchase { total: 12.5, groceries: ${groceries} })".execute.void(driver)
      r <- "MATCH (purchase: Purchase) RETURN purchase.groceries".query(mapper).single(driver)
    } yield {
      r shouldBe groceries
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN []".query(mapper).single(driver)
    }
  }

  it should "work with NonEmptySet" in executeAsFuture { driver =>
    val numbers: Numbers = NonEmptySet.of(1, 3, 5)
    val mapper: ResultMapper[Numbers] = nonEmptySet(mapper = int, order = implicitly)

    // Success.
    for {
      _ <- c"CREATE (: Set { name: 'favorites', numbers: ${numbers} })".execute.void(driver)
      r <- "MATCH (set: Set) RETURN set.numbers".query(mapper).single(driver)
    } yield {
      r shouldBe numbers
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN []".query(mapper).single(driver)
    }
  }

  it should "work with NonEmptyMap" in executeAsFuture { driver =>
    val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)
    val mapper: ResultMapper[Properties] = nonEmptyMap(
      keyMapper = string, valueMapper = boolean, keyOrder = implicitly
    )

    // Success.
    for {
      _ <- c"CREATE (: Config ${properties})".execute.void(driver)
      r <- "MATCH (config: Config) RETURN config".query(mapper).single(driver)
    } yield {
      r shouldBe properties
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN {}".query(mapper).single(driver)
    }
  }

  it should "work with NonEmptyNeoMap" in executeAsFuture { driver =>
    val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)
    val mapper: ResultMapper[Properties] = nonEmptyNeoMap

    // Success.
    for {
      _ <- c"CREATE (: Config ${properties})".execute.void(driver)
      r <- "MATCH (config: Config) RETURN config".query(mapper).single(driver)
    } yield {
      r shouldBe properties
    }

    // Fail if empty.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN {}".query(mapper).single(driver)
    }
  }
}

object CatsDataSpec {
  type Name = Const[String, Int]
  type Messages = Chain[String]
  type Errors = NonEmptyChain[String]
  type Items = NonEmptyList[String]
  type Groceries = NonEmptyVector[String]
  type Numbers = NonEmptySet[Int]
  type Properties = NonEmptyMap[String, Boolean]
}
