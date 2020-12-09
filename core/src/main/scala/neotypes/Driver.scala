package neotypes

import internal.syntax.async._
import internal.syntax.stage._
import internal.syntax.stream._

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

sealed trait StreamingDriver[S[_], F[_]] extends Driver[F] {
  def streamingTransaction(config: TransactionConfig): S[StreamingTransaction[S, F]]

  final def streamingTransaction: S[StreamingTransaction[S, F]] =
    streamingTransaction(config = TransactionConfig.default)

  def streamingTransact[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T]

  final def streamingTransact[T](txF: StreamingTransaction[S, F] => S[T]): S[T] =
    streamingTransact(config = TransactionConfig.default)(txF)
}

object Driver {
  private def txFinalizer[F[_]]: (Transaction[F], Option[Throwable]) => F[Unit] = {
    case (tx, None)    => tx.commit
    case (tx, Some(_)) => tx.rollback
  }

  private class DriverImpl[F[_]](driver: NeoDriver)
                                (implicit F: Async[F]) extends Driver[F] {
    override final def transaction(config: TransactionConfig): F[Transaction[F]] =
      F.async { cb =>
        val (sessionConfig, transactionConfig) = config.getConfigs
        val s = driver.asyncSession(sessionConfig)

        s.beginTransactionAsync(transactionConfig).accept(cb) { tx =>
          Right(Transaction[F](tx, s))
        }
      }

    override final def transact[T](config: TransactionConfig)(txF: Transaction[F] => F[T]): F[T] =
      transaction(config).guarantee(txF)(txFinalizer)

    override final def close: F[Unit] =
      F.async { cb =>
        driver.closeAsync().acceptVoid(cb)
      }
  }

  private final class StreamingDriverImpl[S[_], F[_]](driver: NeoDriver)
                                                     (implicit S: Stream.Aux[S, F], F: Async[F])
                                                     extends DriverImpl[F](driver) with StreamingDriver[S, F] {
    override def streamingTransaction(config: TransactionConfig): S[StreamingTransaction[S, F]] = {
      val (sessionConfig, transactionConfig) = config.getConfigs
      val s = driver.rxSession(sessionConfig)

      s.beginTransaction(transactionConfig).toStream[S].mapS { tx =>
        Transaction[S, F](tx, s)
      }
    }

    override def streamingTransact[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T] = {
      val tx = streamingTransaction(config).single[F].flatMap { opt =>
        F.fromEither(opt.toRight(left = exceptions.TransactionWasNotCreatedException))
      }

      S.resource(tx)(txF)(txFinalizer)
    }
  }

  private[neotypes] def apply[F[_]](driver: NeoDriver)
                                   (implicit F: Async[F]): Driver[F] =
    new DriverImpl(driver)

  private[neotypes] def apply[S[_], F[_]](driver: NeoDriver)
                                         (implicit S: Stream.Aux[S, F], F: Async[F]): StreamingDriver[S, F] =
    new StreamingDriverImpl(driver)
}
