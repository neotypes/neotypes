package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future
import scala.reflect.ClassTag
import org.scalatest.matchers.should.Matchers._


/** Base class for testing the Session[F].transact method. */
final class TransactIntegrationSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationWordSpec(testkit) {
  import TransactIntegrationSpec.CustomException

  private final def ensureCommitedTransaction[T](expectedResults: T)
                                                (txF: Transaction[F] => F[T]): Future[Assertion] =
    executeAsFuture(s => s.transact(txF)).map { results =>
      results shouldBe expectedResults
    }

  private final def ensureRollbackedTransaction[E <: Throwable : ClassTag](txF: Transaction[F] => F[Unit]): Future[Assertion] =
    recoverToSucceededIf[E] {
      executeAsFuture(s => s.transact(txF))
    } flatMap { _ =>
      executeAsFuture(s => "MATCH (n) RETURN count(n)".query[Int].single(s))
    } map { count =>
      count shouldBe 0
    }

  s"Session[${effectName}].transact" should {
    "execute & commit multiple queries inside the same transact" in
      ensureCommitedTransaction(expectedResults = List("Luis", "Dmitry")) { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
          r <- "MATCH (p: PERSON) RETURN p.name".query[String].list(tx)
        } yield r
      }

    "automatically rollback if any query fails inside a transact" in
      ensureRollbackedTransaction[ClientException] { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- "broken cypher query".query[Unit].execute(tx)
        } yield ()
      }

    "automatically rollback if there is an error inside the transact" in
      ensureRollbackedTransaction[CustomException] { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- F.failed[Unit](CustomException)
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        } yield ()
      }
  }
}

object TransactIntegrationSpec {
  final object CustomException extends Throwable
  type CustomException = CustomException.type
}
