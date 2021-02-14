package neotypes

import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._

import org.neo4j.driver.exceptions.ClientException
import org.scalatest.compatible.Assertion

import scala.concurrent.Future
import scala.reflect.ClassTag
import org.scalatest.matchers.should.Matchers._


/** Base class for testing the Driver[F].transact method. */
final class AsyncTransactSpec[F[_]](
  testkit: EffectTestkit[F]
) extends AsyncDriverProvider[F](testkit) with CleaningIntegrationSpec[F] {
  import AsyncTransactSpec.CustomException

  private final def ensureCommittedTransaction[T](expectedResult: T)
                                                 (txF: Transaction[F] => F[T]): Future[Assertion] =
    executeAsFuture(_.transact(txF)).map { result =>
      result shouldBe expectedResult
    }

  private final def ensureRollbackedTransaction[E <: Throwable : ClassTag](
    txF: Transaction[F] => F[Unit]
  ): Future[Assertion] =
    recoverToSucceededIf[E] {
      executeAsFuture(_.transact(txF))
    } flatMap { _ =>
      executeAsFuture(d => "MATCH (n) RETURN count(n)".query[Int].single(d))
    } map ( _ shouldBe 0 )

  s"Driver[${effectName}].transact" should {
    "execute & commit multiple queries inside the same transact" in
      ensureCommittedTransaction(expectedResult = Set("Luis", "Dmitry")) { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
          r <- "MATCH (p: PERSON) RETURN p.name".query[String].set(tx)
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
          _ <- F.fromEither[Unit](Left(CustomException))
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        } yield ()
      }
  }
}

object AsyncTransactSpec {
  final object CustomException extends Throwable
  type CustomException = CustomException.type
}
