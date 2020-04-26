package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.v1.{Session => NSession}

final class Session[F[_]] private[neotypes] (private val session: NSession) extends AnyVal {
  def transaction(implicit F: Async[F]): F[Transaction[F]] =
    F.async { cb =>
      session.beginTransactionAsync().accept(cb) { tx =>
        Right(new Transaction(tx))
      }
    }

  def transact[T](txF: Transaction[F] => F[T])(implicit F: Async[F]): F[T] =
    transaction.guarantee(txF) {
      case (tx, None)    => tx.commit
      case (tx, Some(_)) => tx.rollback
    }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      session.closeAsync().acceptVoid(cb)
    }
}
