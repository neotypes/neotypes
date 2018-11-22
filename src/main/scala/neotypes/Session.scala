package neotypes

import neotypes.Async.AsyncExt
import neotypes.mappers.{ResultMapper, ExecutionMapper}
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

  case class LazySession[T](query: String, params: Map[String, Any] = Map()) {
    def list[F[+ _]](session: Session[F])(implicit rm: ResultMapper[T]): F[Seq[T]] =
      session.transact(tx => list(tx))

    def single[F[+ _]](session: Session[F])(implicit rm: ResultMapper[T]): F[T] =
      session.transact(tx => single(tx))

    def execute[F[+ _]](session: Session[F])(implicit rm: ExecutionMapper[T]): F[T] =
      session.transact(tx => execute(tx))

    def list[F[+ _]](tx: Transaction[F])(implicit rm: ResultMapper[T]): F[Seq[T]] =
      tx.list(query, params)

    def single[F[+ _]](tx: Transaction[F])(implicit rm: ResultMapper[T]): F[T] =
      tx.single(query, params)

    def execute[F[+ _]](tx: Transaction[F])(implicit rm: ExecutionMapper[T]): F[T] =
      tx.execute(query, params)

    def withParams(params: Map[String, Any]): LazySession[T] = this.copy(params = params)
  }

}

