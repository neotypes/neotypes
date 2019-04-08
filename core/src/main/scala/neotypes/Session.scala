package neotypes

import neotypes.implicits._
import neotypes.utils.stage._
import org.neo4j.driver.v1.{Session => NSession, Transaction => NTransaction}

final class Session[F[_]](session: NSession)(implicit F: Async[F]) {
  def beginTransaction(): F[Transaction[F]] =
    F.async { cb =>
      session
        .beginTransactionAsync()
        .accept { tx: NTransaction => cb(Right(new Transaction(tx)(F))) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def close(): F[Unit] =
    F.async { cb =>
      session
        .closeAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def transact[T](txF: Transaction[F] => F[T]): F[T] =
    beginTransaction().flatMap { t =>
      val result = for {
        res <- txF(t)
        _ <- t.commit()
      } yield res

      result.recoverWith { case ex =>
        for {
          _ <- t.rollback()
          res <- F.failed[T](ex)
        } yield res
      }
    }
}
