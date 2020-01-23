package neotypes

import internal.syntax.stage._

import org.neo4j.driver.v1.{Session => NSession}

final class Session[F[_]](private val session: NSession) extends AnyVal {
  def transaction(implicit F: Async[F]): F[Transaction[F]] =
    F.async { cb =>
      session.beginTransactionAsync().accept(cb) { tx =>
        Right(new Transaction(tx))
      }
    }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      session.closeAsync().acceptVoid(cb)
    }
}
