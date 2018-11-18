package neotypes

import neotypes.Async.AsyncExt
import org.neo4j.driver.v1.{Session => NSession}

class Session[F[+ _]](session: NSession)(implicit F: Async[F]) {
  def beginTransaction(): F[Transaction[F]] = {
    F.async[Transaction[F]] { cb =>
      session.beginTransactionAsync().thenAccept(tx =>
        cb(Right(new Transaction(tx)(F)))
      ).exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def close(): F[Unit] = F.async(cb =>
    session.closeAsync()
      .thenAccept(_ => cb(Right(())))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
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

object Session {

  class LazySession[T: RecordMarshallable](query: String, params: Map[String, AnyRef]) {
    def list[F[+ _]](session: Session[F]): F[Seq[T]] =
      session.transact(tx => list(tx))

    def single[F[+ _]](session: Session[F]): F[T] =
      session.transact(tx => single(tx))

    def list[F[+ _]](tx: Transaction[F]): F[Seq[T]] =
      tx.list(query, params)

    def single[F[+ _]](tx: Transaction[F]): F[T] =
      tx.single(query, params)
  }

}

