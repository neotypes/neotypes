package neotypes

import internal.syntax.async._
import internal.syntax.stage._
import internal.syntax.stream._
import org.neo4j.driver.{AccessMode, ConnectionPoolMetrics, Driver => NeoDriver}

import scala.util.Try
import scala.jdk.CollectionConverters._

/** A neotypes driver for accessing the neo4j graph database
  * A driver wrapped in the resource type can be created using the neotypes GraphDatabase
  * {{{
  *    val driver = GraphDatabase.driver[F]("bolt://localhost:7687")
  * }}}
  *
  * @tparam F effect type for driver
  */
sealed trait Driver[F[_]] {
  def transactionConfig: TransactionConfig

  def metrics: F[List[ConnectionPoolMetrics]]

  def transaction(config: TransactionConfig): F[Transaction[F]]

  final def transaction: F[Transaction[F]] =
    transaction(config = transactionConfig)

  def transact[T](config: TransactionConfig)(txF: Transaction[F] => F[T]): F[T]

  def transactReadOnly[T](config: TransactionConfig)(txF: Transaction[F] => F[T]): F[T]

  final def transact[T](txF: Transaction[F] => F[T]): F[T] =
    transact(config = transactionConfig)(txF)

  final def transactReadOnly[T](txF: Transaction[F] => F[T]): F[T] =
    transactReadOnly(config = transactionConfig)(txF)

  /** Close the resources assigned to the neo4j driver.
    *
    *  @return an effect F of Unit.
    */
  def close: F[Unit]

  /** Creates a new driver instance using the provided transaction configuration as default. NB. This does NOT create
   * a new instance of the underlying Java driver.
   */
  def withTransactionConfig(config: TransactionConfig): Driver[F]
}

sealed trait StreamingDriver[S[_], F[_]] extends Driver[F] {
  def streamingTransaction(config: TransactionConfig): S[StreamingTransaction[S, F]]

  final def streamingTransaction: S[StreamingTransaction[S, F]] =
    streamingTransaction(config = transactionConfig)

  def streamingTransact[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T]

  def streamingTransactReadOnly[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T]

  final def streamingTransact[T](txF: StreamingTransaction[S, F] => S[T]): S[T] =
    streamingTransact(config = transactionConfig)(txF)

  final def streamingTransactReadOnly[T](txF: StreamingTransaction[S, F] => S[T]): S[T] =
    streamingTransactReadOnly(config = transactionConfig)(txF)

  override def withTransactionConfig(config: TransactionConfig): StreamingDriver[S, F]
}

object Driver {
  private def txFinalizer[F[_]]: (Transaction[F], Option[Throwable]) => F[Unit] = {
    case (tx, None)    => tx.commit
    case (tx, Some(_)) => tx.rollback
  }

  private class DriverImpl[F[_]](driver: NeoDriver,
                                 override val transactionConfig: TransactionConfig = TransactionConfig.default)
                                (implicit F: Async[F]) extends Driver[F] {
    override final def metrics: F[List[ConnectionPoolMetrics]] =
      F.fromEither(
        Try(driver.metrics).map(_.connectionPoolMetrics.asScala.toList).toEither
      )

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

    def transactReadOnly[T](config: TransactionConfig)(txf: Transaction[F] => F[T]): F[T] =
      transaction(config.withAccessMode(AccessMode.READ)).guarantee(txf)(txFinalizer)

    override final def close: F[Unit] =
      F.async { cb =>
        driver.closeAsync().acceptVoid(cb)
      }

    override def withTransactionConfig(config: TransactionConfig): Driver[F] = new DriverImpl(driver, config)
  }

  private final class StreamingDriverImpl[S[_], F[_]](driver: NeoDriver,
                                                      override val transactionConfig: TransactionConfig =
                                                        TransactionConfig.default)
                                                     (implicit S: Stream.Aux[S, F], F: Async[F])
                                                     extends DriverImpl[F](driver, transactionConfig)
                                                       with StreamingDriver[S, F] {
    override def streamingTransaction(config: TransactionConfig): S[StreamingTransaction[S, F]] = {
      val (sessionConfig, transactionConfig) = config.getConfigs

      val session = F.delay {
        driver.rxSession(sessionConfig)
      }

      S.fromF(session).flatMapS { s =>
        s.beginTransaction(transactionConfig).toStream[S].mapS { tx =>
          Transaction[S, F](tx, s)
        }
      }
    }

    override def streamingTransact[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T] = {
      val tx = streamingTransaction(config).single[F].flatMap { opt =>
        F.fromEither(opt.toRight(left = exceptions.TransactionWasNotCreatedException))
      }

      S.guarantee(tx)(txF)(txFinalizer)
    }

    override def streamingTransactReadOnly[T](config: TransactionConfig)(txF: StreamingTransaction[S, F] => S[T]): S[T] = {
       streamingTransact(config.withAccessMode(AccessMode.READ))(txF)
    }
    override def withTransactionConfig(config: TransactionConfig): StreamingDriver[S, F] =
      new StreamingDriverImpl(driver, config)
  }

  private[neotypes] def apply[F[_]](driver: NeoDriver)
                                   (implicit F: Async[F]): Driver[F] =
    new DriverImpl(driver)

  private[neotypes] def apply[S[_], F[_]](driver: NeoDriver)
                                         (implicit S: Stream.Aux[S, F], F: Async[F]): StreamingDriver[S, F] =
    new StreamingDriverImpl(driver)
}
