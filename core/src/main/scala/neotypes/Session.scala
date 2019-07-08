package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.v1.{Session => NSession, Transaction => NTransaction}

import scala.language.higherKinds

final class Session[F[_]](private val session: NSession) extends AnyVal {
  def transaction[R[_]](implicit F: Async.Aux[F, R]): R[Transaction[F]] =
    F.resource(createTransaction) { transaction => transaction.close }

  private[neotypes] def createTransaction(implicit F: Async[F]): F[Transaction[F]] =
    F.async { cb =>
      session
        .beginTransactionAsync()
        .accept { tx: NTransaction => cb(Right(new Transaction(tx))) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def transact[T](txF: Transaction[F] => F[T])(implicit F: Async[F]): F[T] =
    createTransaction.flatMap { t =>
      val result = for {
        res <- txF(t)
        _ <- t.commit
      } yield res

      result.recoverWith {
        case ex =>
          t.rollback.flatMap {
            _ => F.failed[T](ex)
          }
      }
    }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      session
        .closeAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }
}
