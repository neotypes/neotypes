package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Assertion

import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for integration specs that test the Session.transact method. */
abstract class TransactIntegrationSpec[F[_]] extends CleaningIntegrationSpec[F] {
  def fToFuture[T](f: F[T]): Future[T]

  final def ensureCommitedTransaction[T](expectedResults: T)
                                        (txF: Transaction[F] => F[T])
                                        (implicit F: Async[F]): Future[Assertion] =
    fToFuture(execute(txF)).map {
      results => assert(results == expectedResults)
    }

  final def ensureRollbackedTransaction[E <: Throwable : ClassTag]
                                       (txF: Transaction[F] => F[Unit])
                                       (implicit F: Async[F]): Future[Assertion] =
    recoverToSucceededIf[E] {
      fToFuture(execute(txF))
    } flatMap { _ =>
      fToFuture(execute(s => "MATCH (n) RETURN count(n)".query[Int].single(s)))
    } map {
      count => assert(count == 0)
    }
}

object TransactIntegrationSpec {
  final object CustomException extends Throwable
  type CustomException = CustomException.type
}
