package neotypes

import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import neotypes.internal.syntax.stream._
import neotypes.mappers.ResultMapper

import org.scalatest.matchers.should.Matchers

/** Base class for testing the basic behaviour of transactions. */
trait BaseTransactionSpec[F[_]] extends CleaningIntegrationSpec[F] with Matchers { self: DriverProvider[F] with BaseAsyncSpec[F] =>
  behavior of transactionName

  it should "support explicit commit" in executeAsFuture { d =>
    for {
      tx <- transaction(d)
      _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
      _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
      _ <- tx.commit
      people <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).set(d)
    } yield  {
      people should contain theSameElementsAs Set("Luis", "Dmitry")
    }
  }

  it should "support explicit rollback" in executeAsFuture { d =>
    for {
      tx <- transaction(d)
      _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
      _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
      _ <- tx.rollback
      people <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).list(d)
    } yield {
      people shouldBe empty
    }
  }
}

final class AsyncTransactionSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit) with BaseTransactionSpec[F]

final class StreamTransactionSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit) with BaseTransactionSpec[F] {
  it should "support stream the records" in {
    executeAsFutureList { driver =>
      driver.streamTransaction.flatMapS { tx =>
        "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(tx)
      }
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }
}
