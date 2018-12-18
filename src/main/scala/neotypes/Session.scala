package neotypes

import neotypes.Async.AsyncExt
import neotypes.utils.FunctionUtils._
import org.neo4j.driver.v1.{Session => NSession, Transaction => NTransaction}

class Session[F[_]](session: NSession)(implicit F: Async[F]) {
  def beginTransaction(): F[Transaction[F]] = {
    F.async[Transaction[F]] { cb =>
      session.beginTransactionAsync()
        .thenAccept { tx: NTransaction => cb(Right(new Transaction(tx)(F))) }
        .exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
    }
  }

  def close(): F[Unit] = F.async(cb =>
    session.closeAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
  )

  def transact[T](txF: Transaction[F] => F[T]): F[T] = {
    val tx = beginTransaction()

    val result = for {
      t <- tx
      res <- txF(t)
      _ <- t.commit()
    } yield res

    result.recoverWith { case ex =>
      for {
        t <- tx
        _ <- t.rollback()
        res <- F.failed[T](ex)
      } yield res
    }
  }
}