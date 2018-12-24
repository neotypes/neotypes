package neotypes

import neotypes.Async.AsyncExt
import neotypes.utils.CompletionUtils._
import neotypes.utils.FunctionUtils._
import org.neo4j.driver.v1.{Session => NSession, Transaction => NTransaction}

class Session[F[_]](session: NSession)(implicit F: Async[F]) {
  def beginTransaction(): F[Transaction[F]] = {
    F.async[Transaction[F]] { cb =>
      session.beginTransactionAsync()
        .thenAccept { tx: NTransaction => cb(Right(new Transaction(tx)(F))) }
        .exceptionally(exceptionally(ex => cb(Left(ex))))
    }
  }

  def close(): F[Unit] = F.async(cb =>
    session.closeAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally(exceptionally(ex => cb(Left(ex))))
  )

  def transact[T](txF: Transaction[F] => F[T]): F[T] = {
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
}