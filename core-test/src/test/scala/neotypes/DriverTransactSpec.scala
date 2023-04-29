package neotypes

import neotypes.implicits.syntax.all._
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.MissingRecordException

import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the basic behaviour of using managed transactions. */
trait BaseDriverTransactSpec[F[_]] extends CleaningIntegrationSpec[F] with Matchers { self: DriverProvider[F] with BaseAsyncSpec[F] =>
  import BaseDriverTransactSpec._

  private final def ensureCommittedTransaction[T](expectedResult: T)
                                                 (txF: TransactionType => F[T]): Future[Assertion] =
    executeAsFuture(d => transact(d)(txF)).map { result =>
      result shouldBe expectedResult
    }

  private final def ensureRollbackedTransaction[E <: Throwable : ClassTag](
    txF: TransactionType => F[Unit]
  ): Future[Assertion] =
    recoverToSucceededIf[E] {
      executeAsFuture(d => transact(d)(txF))
    } flatMap { _ =>
      executeAsFuture(d => "MATCH (n) RETURN count(n)".query(ResultMapper.int).single(d))
    } map { count =>
      count shouldBe 0
    }

  it should "execute & commit multiple queries inside the same transact" in
    ensureCommittedTransaction(expectedResult = Set("Luis", "Dmitry")) { tx =>
      for {
        _ <- """CREATE (p: PERSON { name: "Luis" })""".execute.void(tx)
        _ <- """CREATE (p: PERSON { name: "Dmitry" })""".execute.void(tx)
        r <- "MATCH (p: PERSON) RETURN p.name".query(ResultMapper.string).set(tx)
      } yield r
    }

  it should "automatically rollback if any query fails inside a transact" in
    ensureRollbackedTransaction[MissingRecordException.type] { tx =>
      for {
        _ <- """CREATE (p: PERSON { name: "Luis" })""".execute.void(tx)
        _ <- "bad query".execute.void(tx)
      } yield ()
    }

  it should "automatically rollback if there is an error inside the transact" in
    ensureRollbackedTransaction[CustomException.type] { tx =>
      for {
        _ <- """CREATE (p: PERSON { name: "Luis" })""".execute.void(tx)
        _ <- F.fromEither[Unit](Left(CustomException))
        _ <- """CREATE (p: PERSON { name: "Dmitry" })""".execute.void(tx)
      } yield ()
    }
}

object BaseDriverTransactSpec {
  final object CustomException extends Throwable
}

final class AsyncDriverTransactSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit) with BaseDriverTransactSpec[F] {
  behavior of s"AsyncDriver[${asyncName}].transact"
}

final class StreamDriverTransactSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit) with BaseDriverTransactSpec[F] {
  behavior of s"StreamDriver[${streamName}, ${asyncName}].streamTransact"

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
