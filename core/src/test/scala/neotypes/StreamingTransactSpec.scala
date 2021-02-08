package neotypes

import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import neotypes.internal.syntax.stream._

import org.neo4j.driver.exceptions.ClientException
import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the StreamingDriver[S, F].streamingTransact method. */
final class StreamingTransactSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider[S, F](testkit) with CleaningIntegrationSpec[F] with Matchers {
  behavior of s"StreamingDriver[${streamName}, ${effectName}].streamingTransact"

  import StreamingTransactSpec.CustomException

  private final def ensureCommittedTransaction[T](expectedResults: Iterable[T])
                                                 (txF: StreamingTransaction[S, F] => S[T]): Future[Assertion] =
    executeAsFutureList(_.streamingTransact(txF)).map { results =>
      results should contain theSameElementsAs expectedResults
    }

  private final def ensureRollbackedTransaction[E <: Throwable : ClassTag](
    txF: StreamingTransaction[S, F] => F[Unit]
  ): Future[Assertion] =
    recoverToSucceededIf[E] {
      executeAsFutureList(_.streamingTransact(txF andThen S.fromF))
    } flatMap { _ =>
      executeAsFuture(s => "MATCH (n) RETURN count(n)".query[Int].single(s))
    } map { count =>
      count shouldBe 0
    }

  it should "execute & commit multiple queries inside the same transact" in
    ensureCommittedTransaction(expectedResults = Set("Luis", "Dmitry")) { tx =>
      val create = for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()

      S.fromF(create).flatMapS { _ =>
        "MATCH (p: PERSON) RETURN p.name".query[String].stream(tx)
      }
    }

  it should "automatically rollback if any query fails inside a transact" in
    ensureRollbackedTransaction[ClientException] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "broken cypher query".query[Unit].execute(tx)
      } yield ()
    }

  it should "automatically rollback if there is an error inside the transact" in
    ensureRollbackedTransaction[CustomException] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- F.fromEither[Unit](Left(CustomException))
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()
    }
}

object StreamingTransactSpec {
  final object CustomException extends Throwable
  type CustomException = CustomException.type
}
