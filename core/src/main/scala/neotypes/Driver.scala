package neotypes

import internal.syntax.async._
import internal.syntax.stream._
import model.exceptions.TransactionWasNotCreatedException

import org.neo4j.driver.{ConnectionPoolMetrics, Driver => NeoDriver}
import org.neo4j.driver.async.AsyncSession
import org.neo4j.driver.reactive.ReactiveSession

import scala.util.Try
import scala.jdk.CollectionConverters._

/** A neotypes async driver for accessing the neo4j graph database. A driver wrapped in the resource type can be created
  * using the neotypes GraphDatabase:
  * {{{
  *    val driver = GraphDatabase.driver[F]("bolt://localhost:7687")
  * }}}
  *
  * @tparam F
  *   Async type for driver
  */
sealed trait AsyncDriver[F[_]] {
  def metrics: F[List[ConnectionPoolMetrics]]

  def transaction(config: TransactionConfig): F[AsyncTransaction[F]]

  final def transaction: F[AsyncTransaction[F]] =
    transaction(config = TransactionConfig.default)

  def transact[T](config: TransactionConfig)(txF: AsyncTransaction[F] => F[T]): F[T]

  final def transact[T](txF: AsyncTransaction[F] => F[T]): F[T] =
    transact(config = TransactionConfig.default)(txF)

  /** Close the resources assigned to the neo4j driver. */
  def close: F[Unit]
}

/** A neotypes stream driver for accessing the neo4j graph database. A driver wrapped in the resource type can be
  * created using the neotypes GraphDatabase:
  * {{{
  *    val driver = GraphDatabase.streamDriver[S, F]("bolt://localhost:7687")
  * }}}
  *
  * @tparam S
  *   Stream type for driver
  * @tparam F
  *   Async type for driver
  */
sealed trait StreamDriver[S[_], F[_]] extends AsyncDriver[F] {
  def streamTransaction(config: TransactionConfig): S[StreamTransaction[S, F]]

  final def streamTransaction: S[StreamTransaction[S, F]] =
    streamTransaction(config = TransactionConfig.default)

  def streamTransact[T](config: TransactionConfig)(txF: StreamTransaction[S, F] => S[T]): S[T]

  final def streamTransact[T](txF: StreamTransaction[S, F] => S[T]): S[T] =
    streamTransact(config = TransactionConfig.default)(txF)
}

object Driver {
  private def txFinalizer[F[_]]: (AsyncTransaction[F], Option[Throwable]) => F[Unit] = {
    case (tx, None)    => tx.commit
    case (tx, Some(_)) => tx.rollback
  }

  private class AsyncDriverImpl[F[_]](
    driver: NeoDriver
  )(implicit
    F: Async[F]
  ) extends AsyncDriver[F] {
    override final def metrics: F[List[ConnectionPoolMetrics]] =
      F.fromEither(
        Try(driver.metrics).map(_.connectionPoolMetrics.asScala.toList).toEither
      )

    override final def transaction(config: TransactionConfig): F[AsyncTransaction[F]] =
      F.delay(config.getConfigs).flatMap { case (sessionConfig, transactionConfig) =>
        F.delay(driver.session(classOf[AsyncSession], sessionConfig)).flatMap { s =>
          F.fromCompletionStage(s.beginTransactionAsync(transactionConfig)).map { tx =>
            Transaction.async[F](tx, () => F.fromCompletionStage(s.closeAsync()).void)
          }
        }
      }

    override final def transact[T](config: TransactionConfig)(txF: AsyncTransaction[F] => F[T]): F[T] =
      transaction(config).guarantee(txF)(txFinalizer)

    override final def close: F[Unit] =
      F.fromCompletionStage(driver.closeAsync()).void
  }

  private final class StreamDriverImpl[S[_], F[_]](
    driver: NeoDriver
  )(implicit
    S: Stream.Aux[S, F],
    F: Async[F]
  ) extends AsyncDriverImpl[F](driver)
      with StreamDriver[S, F] {
    override final def streamTransaction(config: TransactionConfig): S[StreamTransaction[S, F]] = {
      val (sessionConfig, transactionConfig) = config.getConfigs

      val session = F.delay(driver.session(classOf[ReactiveSession], sessionConfig))

      S.fromF(session).flatMapS { s =>
        S.fromPublisher(s.beginTransaction(transactionConfig)).mapS { tx =>
          Transaction.stream[S, F](tx, () => S.fromPublisher(s.close()).voidS)
        }
      }
    }

    override final def streamTransact[T](config: TransactionConfig)(txF: StreamTransaction[S, F] => S[T]): S[T] = {
      val tx = streamTransaction(config).single[F].flatMap { opt =>
        F.fromEither(opt.toRight(left = TransactionWasNotCreatedException))
      }

      S.guarantee(tx)(txF)(txFinalizer)
    }
  }

  private[neotypes] def async[F[_]](driver: NeoDriver)(implicit F: Async[F]): AsyncDriver[F] =
    new AsyncDriverImpl(driver)

  private[neotypes] def stream[S[_], F[_]](
    driver: NeoDriver
  )(implicit
    S: Stream.Aux[S, F],
    F: Async[F]
  ): StreamDriver[S, F] =
    new StreamDriverImpl(driver)
}
