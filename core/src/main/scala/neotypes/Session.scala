package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.v1.{Session => NSession, Transaction => NTransaction}

import scala.language.higherKinds

final class Session[F[_]](private val session: NSession) extends AnyVal {
  def transaction(implicit F: Async[F]): F[Transaction[F]] =
    F.async { cb =>
      session
        .beginTransactionAsync()
        .accept { tx: NTransaction => cb(Right(new Transaction(tx))) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def transact[T](txF: Transaction[F] => F[T])(implicit F: Async[F]): F[T] =
    transaction.guarantee(txF) {
      case (tx, None)    => tx.commit
      case (tx, Some(_)) => tx.rollback
    }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      session
        .closeAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }
}
