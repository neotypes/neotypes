package neotypes

import neotypes.internal.syntax.async._
import neotypes.internal.syntax.stream._
import neotypes.mappers.ResultMapper
import neotypes.syntax.all._

/** Base class for testing the basic behaviour of transactions. */
sealed trait BaseTransactionSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  behavior of transactionName

  it should "support explicit commit" in executeAsFuture { driver =>
    for {
      tx <- transaction(driver)
      _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
      _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
      _ <- tx.commit
      people <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).set(driver)
    } yield {
      people should contain theSameElementsAs Set("Luis", "Dmitry")
    }
  }

  it should "support explicit rollback" in executeAsFuture { driver =>
    for {
      tx <- transaction(driver)
      _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
      _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
      _ <- tx.rollback
      people <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).list(driver)
    } yield {
      people shouldBe empty
    }
  }
}

final class AsyncTransactionSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseTransactionSpec[F]

final class StreamTransactionSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseTransactionSpec[F] {
  it should "support stream the records" in {
    executeAsFutureList { driver =>
      driver.streamTransaction.flatMapS { tx =>
        "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(tx) andThen S.fromF(tx.commit)
      } collect { case Left(i) =>
        i
      }
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }
}
