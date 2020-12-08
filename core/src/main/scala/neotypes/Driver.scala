package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.{Driver => NeoDriver}

/** A neotypes driver for accessing the neo4j graph database
  * A driver wrapped in the resource type can be created using the neotypes GraphDatabase
  * {{{
  *    val driver = GraphDatabase.driver[F]("bolt://localhost:7687")
  * }}}
  *
  * @tparam F effect type for driver
  */
sealed trait Driver[F[_]] {
  def transaction(config: TransactionConfig): F[Transaction[F]]

  final def transaction: F[Transaction[F]]  =
    transaction(config = TransactionConfig.default)

  def transact[T](config: TransactionConfig)(txF: Transaction[F] => F[T]): F[T]

  final def transact[T](txF: Transaction[F] => F[T]): F[T] =
    transact(config = TransactionConfig.default)(txF)

  /** Close the resources assigned to the neo4j driver.
    *
    *  @return an effect F of Unit.
    */
  def close: F[Unit]
}

object Driver {
  private def txFinalizer[F[_]]: (Transaction[F], Option[Throwable]) => F[Unit] = {
    case (tx, None)    => tx.commit
    case (tx, Some(_)) => tx.rollback
  }

  private[neotypes] def apply[F[_]](driver: NeoDriver)(implicit F: Async[F]): Driver[F] =
    new Driver[F] {
      override final def transaction(config: TransactionConfig): F[Transaction[F]] =
        F.async { cb =>
          val (sessionConfig, transactionConfig) = config.getConfigs
          val s = driver.asyncSession(sessionConfig)

          s.beginTransactionAsync(transactionConfig).accept(cb) { tx =>
            Right(Transaction[F](tx, s))
          }
        }

      override final def transact[T](config: TransactionConfig)(txF: Transaction[F] => F[T]): F[T] =
        transaction(config).guarantee(txF)(Driver.txFinalizer)

      override final def close: F[Unit] =
        F.async { cb =>
          driver.closeAsync().acceptVoid(cb)
        }
    }
}
