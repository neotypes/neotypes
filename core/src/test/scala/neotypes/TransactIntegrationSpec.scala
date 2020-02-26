package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the Session[F].transact method. */
abstract class TransactIntegrationSpec[F[_]] (implicit ct: ClassTag[F[_]]) extends CleaningIntegrationSpec[F] {
  private val effectName: String = ct.runtimeClass.getCanonicalName
  behavior of s"Session[${effectName}].transact"

  import TransactIntegrationSpec.CustomException

  def fToFuture[T](f: F[T]): Future[T]

  implicit def F: Async[F]

  private final def ensureCommitedTransaction[T](expectedResults: T)
                                                (txF: Transaction[F] => F[T]): Future[Assertion] =
    fToFuture(
      execute(s => s.transact(txF)).map {
        results => assert(results == expectedResults)
      }
    )

  private final def ensureRollbackedTransaction[E <: Throwable : ClassTag](txF: Transaction[F] => F[Unit]): Future[Assertion] =
    recoverToSucceededIf[E] {
      fToFuture(execute(s => s.transact(txF)))
    } flatMap { _ =>
      fToFuture(execute(s => "MATCH (n) RETURN count(n)".query[Int].single(s)))
    } map {
      count => assert(count == 0)
    }

  it should "execute & commit multiple queries inside the same transact" in
    ensureCommitedTransaction(expectedResults = List("Luis", "Dmitry")) { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        r <- "MATCH (p: PERSON) RETURN p.name".query[String].list(tx)
      } yield r
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
        _ <- F.failed[Unit](CustomException)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()
    }
}

object TransactIntegrationSpec {
  final object CustomException extends Throwable
  type CustomException = CustomException.type
}
