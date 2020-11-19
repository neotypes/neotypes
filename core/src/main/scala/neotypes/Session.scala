package neotypes

import java.util.concurrent.{CompletableFuture, CompletionStage}

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.{TransactionConfig => NeoTransactionConfig}
import org.neo4j.driver.async.{AsyncTransaction, AsyncTransactionWork, AsyncSession => NeoAsyncSession}

sealed trait Session[F[_]] {
  def transaction: F[Transaction[F]]

  def transaction(config: NeoTransactionConfig): F[Transaction[F]]

  def transact[T](txF: Transaction[F] => F[T]): F[T]

  def transact[T](config: NeoTransactionConfig)(txF: Transaction[F] => F[T]): F[T]

  def readOnlyTransact[T](config: NeoTransactionConfig)(txf: Transaction[F] => F[T]): F[T]

  def readOnlyTransact[T](txf: Transaction[F] => F[T]): F[T]

  def close: F[Unit]
}

object Session {
  private[neotypes] def apply[F[_]](F: Async[F], session: NeoAsyncSession)
                                   (lock: F.Lock): Session[F] = new Session[F] {
    private implicit final val FF: Async[F] = F

    override final def transaction: F[Transaction[F]] =
      transaction(NeoTransactionConfig.empty)

    override final def transaction(config: NeoTransactionConfig): F[Transaction[F]] =
      lock.acquire.flatMap { _ =>
        F.async { cb =>
          session.beginTransactionAsync(config).accept(cb) { tx =>
            Right(Transaction(F, tx)(lock))
          }
        }
      }

    override final def transact[T](txF: Transaction[F] => F[T]): F[T] =
      transact(NeoTransactionConfig.empty)(txF)

    override final def transact[T](config: NeoTransactionConfig)(txF: Transaction[F] => F[T]): F[T] =
      transaction(config).guarantee(txF) {
        case (tx, None)    => tx.commit
        case (tx, Some(_)) => tx.rollback
      }

    override final def readOnlyTransact[T](txf: Transaction[F] => F[T]): F[T] =
      readOnlyTransact(NeoTransactionConfig.empty())(txf)

    override final def readOnlyTransact[T](config: NeoTransactionConfig)(txf: Transaction[F] => F[T]): F[T] =
      lock.acquire.flatMap { _ =>
        F.async { cb =>
          session.readTransactionAsync(new AsyncTransactionWork[CompletionStage[T]] {
            override def execute(tx: AsyncTransaction) = {
              val completableF = new CompletableFuture[T]()
              txf(Transaction(F, tx)(lock)).map(completableF.complete).recover{
                case th: Throwable => completableF.completeExceptionally(th)
              }
              completableF
            }
          }, config).accept(cb){
            case t: Throwable => Left(t)
            case v => Right(v)
          }
        }
      }

    override final def close: F[Unit] =
      F.async { cb =>
        session.closeAsync().acceptVoid(cb)
      }
  }
}
