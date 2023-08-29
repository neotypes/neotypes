package neotypes.cats.data

import cats.data.{Chain, Const, NonEmptyChain, NonEmptyList, NonEmptyMap, NonEmptySet, NonEmptyVector}
import neotypes._
import neotypes.cats.data.mappers._
import neotypes.internal.syntax.async._
import neotypes.mappers.{KeyMapper, ResultMapper}
import neotypes.model.exceptions.IncoercibleException
import neotypes.syntax.all._

/** Base class for testing the use of the library with cats.data data types. */
sealed trait BaseCatsDataSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  import BaseCatsDataSpec._

  behavior of s"${driverName} with cats.data types"

  it should "work with Const" in executeAsFuture { driver =>
    val name: Name = Const("Balmung")

    for {
      _ <- c"CREATE (: User { name: ${name} })".execute.void(driver)
      r <- "MATCH (user: User) RETURN user.name".query(Name.resultMapper).single(driver)
    } yield {
      r shouldBe name
    }
  }

  it should "work with Chain" in executeAsFuture { driver =>
    val messages: Messages = Chain("a", "b")
    for {
      _ <- c"CREATE (: Chat { user1: 'Balmung', user2: 'Luis', messages: ${messages} })".execute.void(driver)
      r <- "MATCH (chat: Chat) RETURN chat.messages".query(Messages.resultMapper).single(driver)
    } yield {
      r shouldBe messages
    }
  }

  it should "work with NonEmptyChain" in executeAsFuture { driver =>
    val errors: Errors = NonEmptyChain("a", "b")

    for {
      _ <- c"CREATE (: StackTrace { line: 1, errors: ${errors} })".execute.void(driver)
      r <- "MATCH (stackTrace: StackTrace) RETURN stackTrace.errors".query(Errors.resultMapper).single(driver)
    } yield {
      r shouldBe errors
    }
  }

  it should "fail if trying to query a empty Neo4j list as a cats NonEmptyChain" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN []".query(Errors.resultMapper).single(driver)
      }
    }
  }

  it should "work with NonEmptyList" in executeAsFuture { driver =>
    val items: Items = NonEmptyList.of("a", "b")

    for {
      _ <- c"CREATE (: Player { name: 'Luis', items: ${items} })".execute.void(driver)
      r <- "MATCH (player: Player) RETURN player.items".query(Items.resultMapper).single(driver)
    } yield {
      r shouldBe items
    }
  }

  it should "fail if trying to query a empty Neo4j list as a cats NonEmptyList" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN []".query(Items.resultMapper).single(driver)
      }
    }
  }

  it should "work with NonEmptyVector" in executeAsFuture { driver =>
    val groceries: Groceries = NonEmptyVector.of("a", "b")

    for {
      _ <- c"CREATE (: Purchase { total: 12.5, groceries: ${groceries} })".execute.void(driver)
      r <- "MATCH (purchase: Purchase) RETURN purchase.groceries".query(Groceries.resultMapper).single(driver)
    } yield {
      r shouldBe groceries
    }
  }

  it should "fail if trying to query a empty Neo4j list as a cats NonEmptyVector" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN []".query(Groceries.resultMapper).single(driver)
      }
    }
  }

  it should "work with NonEmptySet" in executeAsFuture { driver =>
    val numbers: Numbers = NonEmptySet.of(1, 3, 5)

    for {
      _ <- c"CREATE (: Set { name: 'favorites', numbers: ${numbers} })".execute.void(driver)
      r <- "MATCH (set: Set) RETURN set.numbers".query(Numbers.resultMapper).single(driver)
    } yield {
      r shouldBe numbers
    }
  }

  it should "fail if trying to query a empty Neo4j list as a cats NonEmptySet" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN []".query(Numbers.resultMapper).single(driver)
      }
    }
  }

  it should "work with NonEmptyMap" in executeAsFuture { driver =>
    val bag: Bag = NonEmptyMap.of(1 -> "foo", 3 -> "bar", 5 -> "baz")

    for {
      _ <- c"UNWIND ${bag} AS element CREATE (: BagElement { key: HEAD(element), value: HEAD(TAIL(element)) })"
        .execute
        .void(driver)
      r <- "MATCH (element: BagElement) RETURN collect([element.key, element.value])"
        .query(Bag.resultMapper)
        .single(driver)
    } yield {
      r shouldBe bag
    }
  }

  it should "fail if trying to query a empty Neo4j list as a cats NonEmptyMap" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN []".query(Bag.resultMapper).single(driver)
      }
    }
  }

  it should "work with NonEmptyNeoMap" in executeAsFuture { driver =>
    val properties: Properties = NonEmptyMap.of("a" -> true, "b" -> false)

    for {
      _ <- c"CREATE (: Config ${properties})".execute.void(driver)
      r <- "MATCH (config: Config) RETURN config".query(Properties.resultMapper).single(driver)
    } yield {
      r shouldBe properties
    }
  }

  it should "fail if trying to query a empty Neo4j map as a cats NonEmptyNeoMap" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN {}".query(Properties.resultMapper).single(driver)
      }
    }
  }
}

object BaseCatsDataSpec {
  import ResultMapper._

  type Name = Const[String, Int]
  object Name {
    val resultMapper: ResultMapper[Name] = const(string)
  }

  type Messages = Chain[String]
  object Messages {
    val resultMapper: ResultMapper[Messages] = chain(string)
  }

  type Errors = NonEmptyChain[String]
  object Errors {
    val resultMapper: ResultMapper[Errors] = nonEmptyChain(string)
  }

  type Items = NonEmptyList[String]
  object Items {
    val resultMapper: ResultMapper[Items] = nonEmptyList(string)
  }

  type Groceries = NonEmptyVector[String]
  object Groceries {
    val resultMapper: ResultMapper[Groceries] = nonEmptyVector(string)
  }

  type Numbers = NonEmptySet[Int]
  object Numbers {
    val resultMapper: ResultMapper[Numbers] = nonEmptySet(mapper = int, order = implicitly)
  }

  type Bag = NonEmptyMap[Int, String]
  object Bag {
    val resultMapper: ResultMapper[Bag] = nonEmptyMap(
      keyMapper = int,
      valueMapper = string,
      keyOrder = implicitly
    )
  }

  type Properties = NonEmptyMap[String, Boolean]
  object Properties {
    val resultMapper: ResultMapper[Properties] = nonEmptyNeoMap(
      keyMapper = KeyMapper.string,
      valueMapper = boolean,
      keyOrder = implicitly
    )
  }
}

final class AsyncCatsDataSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseCatsDataSpec[F]

final class StreamCatsDataSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseCatsDataSpec[F]
