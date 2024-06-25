package neotypes

import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.MissingRecordException
import neotypes.syntax.all._
import org.scalatest.Assertion

import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the basic behaviour of using managed transactions. */
sealed trait BaseDriverTransactSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  import BaseDriverTransactSpec._

  behavior of s"${driverName} managed transaction ${transactionName}"

  private final def ensureCommittedTransaction[T](
    expectedResult: T
  )(
    txF: TransactionType => F[T]
  ): Future[Assertion] =
    executeAsFuture(driver => transact(driver)(txF)).map { result =>
      result shouldBe expectedResult
    }

  private final def ensureRolledBackTransaction[E <: Throwable: ClassTag](
    txF: TransactionType => F[Unit]
  ): Future[Assertion] =
    recoverToSucceededIf[E] {
      executeAsFuture(driver => transact(driver)(txF))
    } flatMap { _ =>
      executeAsFuture(driver => "MATCH (n) RETURN count(n)".query(ResultMapper.int).single(driver))
    } map { count =>
      count shouldBe 0
    }

  it should "execute & commit multiple queries inside the same transact" in
    ensureCommittedTransaction(expectedResult = Set("Luis", "Dmitry")) { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
        _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
        r <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).set(tx)
      } yield r
    }

  it should "automatically rollback if any query fails inside a transact" in
    ensureRolledBackTransaction[MissingRecordException] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
        _ <- "bad query".execute.void(tx)
      } yield ()
    }

  it should "automatically rollback if there is an error inside the transact" in
    ensureRolledBackTransaction[CustomException.type] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: 'Luis' })".execute.void(tx)
        _ <- F.fromEither[Unit](Left(CustomException))
        _ <- "CREATE (p: PERSON { name: 'Dmitry' })".execute.void(tx)
      } yield ()
    }
}

object BaseDriverTransactSpec {
  object CustomException extends Throwable
}

final class AsyncDriverTransactSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseDriverTransactSpec[F]

final class StreamDriverTransactSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseDriverTransactSpec[F] {
  it should "support stream the records" in {
    executeAsFutureList { driver =>
      driver.streamTransact { tx =>
        "UNWIND [1, 2, 3] AS x RETURN x".query(ResultMapper.int).stream(tx)
      }
    } map { ints =>
      ints shouldBe List(1, 2, 3)
    }
  }
}
