package neotypes

import internal.syntax.async._
import internal.syntax.stage._
import internal.syntax.stream._

import org.neo4j.driver.{TransactionConfig => NeoTransactionConfig}
import org.neo4j.driver.async.{AsyncSession => NeoAsyncSession}
import org.neo4j.driver.reactive.{RxSession => NeoRxSession}

sealed trait Session[F[_]] {
  final def transaction: F[Transaction[F]] =
    transaction(NeoTransactionConfig.empty)

  def transaction(config: NeoTransactionConfig): F[Transaction[F]]

  final def transact[T](txF: Transaction[F] => F[T]): F[T] =
    transact(NeoTransactionConfig.empty)(txF)

  def transact[T](config: NeoTransactionConfig)(txF: Transaction[F] => F[T]): F[T]

  def close: F[Unit]
}

sealed trait StreamingSession[S[_], F[_]] extends Session[F] {
  final def streamingTransaction: S[StreamingTransaction[S, F]] =
    streamingTransaction(NeoTransactionConfig.empty)

  def streamingTransaction(config: NeoTransactionConfig): S[StreamingTransaction[S, F]]

  final def streamingTransact[T](txF: StreamingTransaction[S, F] => S[T]): S[T] =
    streamingTransact(NeoTransactionConfig.empty)(txF)

  def streamingTransact[T](config: NeoTransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T]
}

object Session {
  private def txFinalizer[F[_]]: (Transaction[F], Option[Throwable]) => F[Unit] = {
    case (tx, None)    => tx.commit
    case (tx, Some(_)) => tx.rollback
  }

  private[neotypes] def apply[F[_]](F: Async[F], session: NeoAsyncSession)
                                   (lock: F.Lock): Session[F] = new Session[F] {
    private implicit final val FF: Async[F] = F

    override final def transaction(config: NeoTransactionConfig): F[Transaction[F]] =
      lock.acquire.flatMap { _ =>
        F.async { cb =>
          session.beginTransactionAsync(config).accept(cb) { tx =>
            Right(Transaction(F, tx)(lock))
          }
        }
      }

    override final def transact[T](config: NeoTransactionConfig)(txF: Transaction[F] => F[T]): F[T] =
      transaction(config).guarantee(txF)(txFinalizer)

    override final def close: F[Unit] =
      F.async { cb =>
        session.closeAsync().acceptVoid(cb)
      }
  }

  private[neotypes] def apply[S[_], F[_]](F: Async[F], S: Stream.Aux[S, F], session: NeoRxSession)
                                         (lock: F.Lock): StreamingSession[S, F] = new StreamingSession[S, F] {
    private implicit final val FF: Async[F] = F
    private implicit final val SS: Stream.Aux[S, F] = S

    override final def transaction(config: NeoTransactionConfig): F[Transaction[F]] =
      streamingTransaction(config).single[F].widden

    override final def streamingTransaction(config: NeoTransactionConfig): S[StreamingTransaction[S, F]] =
      S.fromF(lock.acquire).flatMapS { _ =>
        session.beginTransaction(config).toStream[S].mapS { tx =>
          Transaction(F, S, tx)(lock)
        }
      }

    override final def transact[T](config: NeoTransactionConfig)(txF: Transaction[F] => F[T]): F[T] =
      transaction(config).guarantee(txF)(txFinalizer)

    override final def streamingTransact[T](config: NeoTransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T] =
      S.resource(streamingTransaction(config).single[F])(txFinalizer).flatMapS(txF)

    override final def close: F[Unit] =
      session.close().toStream[S].void[F]
  }
}
