package neotypes

import java.util.concurrent.CompletionStage

import internal.utils.traverse._
import internal.syntax.async._
import internal.syntax.stage._
import internal.syntax.stream._
import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import org.neo4j.driver.{Record, Value}
import org.neo4j.driver.async.{AsyncTransaction => NeoAsyncTransaction}
import org.neo4j.driver.exceptions.NoSuchRecordException
import org.neo4j.driver.reactive.{RxTransaction => NeoRxTransaction}

import scala.collection.compat._
import scala.jdk.CollectionConverters._

sealed trait Transaction[F[_]] {
  def execute[T](query: String, params: Map[String, QueryParam] = Map.empty)
                (implicit executionMapper: ExecutionMapper[T]): F[T]

  def collectAs[C, T](factory: Factory[T, C])
                     (query: String, params: Map[String, QueryParam] = Map.empty)
                     (implicit resultMapper: ResultMapper[T]): F[C]

  def list[T](query: String, params: Map[String, QueryParam] = Map.empty)
             (implicit resultMapper: ResultMapper[T]): F[List[T]]

  def map[K, V](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit resultMapper: ResultMapper[(K, V)]): F[Map[K, V]]

  def set[T](query: String, params: Map[String, QueryParam] = Map.empty)
            (implicit resultMapper: ResultMapper[T]): F[Set[T]]

  def vector[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit resultMapper: ResultMapper[T]): F[Vector[T]]

  def single[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit resultMapper: ResultMapper[T]): F[T]

  def stream[T, S[_]](query: String, params: Map[String, QueryParam] = Map.empty)
                     (implicit S: Stream.Aux[S, F], resultMapper: ResultMapper[T]): S[T]

  def commit: F[Unit]

  def rollback: F[Unit]
}

sealed trait StreamingTransaction[S[_], F[_]] extends Transaction[F] {
  def streamRx[T](query: String, params: Map[String, QueryParam] = Map.empty)
                 (implicit resultMapper: ResultMapper[T]): S[T]
}

object Transaction {
  private def recordToList(record: Record): List[(String, Value)] =
    record.fields.asScala.iterator.map(p => p.key -> p.value).toList

  private[neotypes] def apply[F[_]](F: Async[F], transaction: NeoAsyncTransaction)
                                   (lock: F.Lock): Transaction[F] = new Transaction[F] {
    private implicit final val FF: Async[F] = F

    private def collectAsImpl[T, C](transaction: NeoAsyncTransaction, query: String, params: Map[String, QueryParam])
                                   (traverseFun: Iterator[Record] => (Record => Either[Throwable, T]) => Either[Throwable, C])
                                   (implicit resultMapper: ResultMapper[T]): F[C] =
      F.async { cb =>
        transaction
          .runAsync(query, QueryParam.toJavaMap(params))
          .thenCompose(_.listAsync())
          .accept(cb) { records =>
            traverseFun(records.asScala.iterator) { record =>
              resultMapper.to(recordToList(record), None)
            }
          }
      }

    override final def execute[T](query: String, params: Map[String, QueryParam])
                                 (implicit executionMapper: ExecutionMapper[T]): F[T] =
      F.async { cb =>
        transaction
          .runAsync(query, QueryParam.toJavaMap(params))
          .thenCompose(_.consumeAsync())
          .accept(cb)(executionMapper.to)
      }

    override final def collectAs[C, T](factory: Factory[T, C])
                                      (query: String, params: Map[String, QueryParam])
                                      (implicit resultMapper: ResultMapper[T]): F[C] =
      collectAsImpl(transaction, query, params)(traverseAs(factory))

    override final def list[T](query: String, params: Map[String, QueryParam])
                              (implicit resultMapper: ResultMapper[T]): F[List[T]] =
      collectAsImpl(transaction, query, params)(traverseAsList[Record, T])

    override final def map[K, V](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[(K, V)]): F[Map[K,V]] =
      collectAsImpl(transaction, query, params)(traverseAsMap[Record, K, V])

    override final def set[T](query: String, params: Map[String, QueryParam])
                             (implicit resultMapper: ResultMapper[T]): F[Set[T]] =
      collectAsImpl(transaction, query, params)(traverseAsSet[Record, T])

    override final def vector[T](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[T]): F[Vector[T]] =
      collectAsImpl(transaction, query, params)(traverseAsVector[Record, T])

    override final def single[T](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[T]): F[T] =
      F.async { cb =>
        transaction
          .runAsync(query, QueryParam.toJavaMap(params))
          .thenCompose(_.singleAsync())
          .acceptExceptionally(cb) { record =>
            resultMapper.to(recordToList(record), None)
          } {
            case _: NoSuchRecordException => resultMapper.to(List.empty, None)
          }
      }

    private def nextAsyncToF[T](cs: CompletionStage[Record])
                               (implicit resultMapper: ResultMapper[T]): F[Option[T]] =
      F.async { cb =>
        cs.accept(cb) { r =>
          Option(r) match {
            case None =>
              Right(None)

            case Some(record) =>
              resultMapper.to(recordToList(record), None).map(r => Option(r))
          }
        }
      }

    override final def stream[T, S[_]](query: String, params: Map[String, QueryParam])
                                      (implicit S: Stream.Aux[S, F], resultMapper: ResultMapper[T]): S[T] =
      S.fToS(
        F.async { cb =>
          transaction
            .runAsync(query, QueryParam.toJavaMap(params))
            .accept(cb) { statementResultCursor =>
              Right(S.init(() => nextAsyncToF(statementResultCursor.nextAsync())))
            }
        }
      )

    override final def commit: F[Unit] =
      F.async[Unit] { cb =>
        transaction.commitAsync().acceptVoid(cb)
      } guarantee { _ =>
        lock.release
      }

    override final def rollback: F[Unit] =
      F.async[Unit] { cb =>
        transaction.rollbackAsync().acceptVoid(cb)
      } guarantee { _ =>
        lock.release
      }
  }

  private[neotypes] def apply[S[_], F[_]](F: Async[F], S: Stream.Aux[S, F], transaction: NeoRxTransaction)
                                         (lock: F.Lock): StreamingTransaction[S, F] = new StreamingTransaction[S, F] {
    private implicit final val FF: Async[F] = F
    private implicit final val SS: Stream.Aux[S, F] = S

    override final def execute[T](query: String, params: Map[String, QueryParam])(implicit executionMapper: ExecutionMapper[T]): F[T] =
      transaction
        .run(query, QueryParam.toJavaMap(params))
        .consume()
        .toStream
        .single[F]
        .flatMap(rs => F.fromEither(executionMapper.to(rs)))

    override final def collectAs[C, T](factory: Factory[T, C])
                                      (query: String, params: Map[String, QueryParam])
                                      (implicit resultMapper: ResultMapper[T]): F[C] =
      streamRx(query, params).collectAs[F, C](factory)

    override final def list[T](query: String, params: Map[String, QueryParam])
                              (implicit resultMapper: ResultMapper[T]): F[List[T]] =
      collectAs[List[T], T](List)(query, params)

    override final def map[K, V](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[(K, V)]): F[Map[K,V]] =
      collectAs[Map[K, V], (K, V)](Map)(query, params)

    override final def set[T](query: String, params: Map[String, QueryParam])
                             (implicit resultMapper: ResultMapper[T]): F[Set[T]] =
      collectAs[Set[T], T](Set)(query, params)

    override final def vector[T](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[T]): F[Vector[T]] =
      collectAs[Vector[T], T](Vector)(query, params)

    override final def single[T](query: String, params: Map[String, QueryParam])
                                (implicit resultMapper: ResultMapper[T]): F[T] =
      streamRx(query, params).single[F]

    override final def stream[T, S1[_]](query: String, params: Map[String, QueryParam])
                                       (implicit S1: Stream.Aux[S1, F], resultMapper: ResultMapper[T]): S1[T] =
      streamRx(query, params).asInstanceOf[S1[T]]

    override final def streamRx[T](query: String, params: Map[String, QueryParam])
                                  (implicit resultMapper: ResultMapper[T]): S[T] =
      transaction
        .run(query, QueryParam.toJavaMap(params))
        .records()
        .toStream
        .evalMap(r => F.fromEither(resultMapper.to(recordToList(r), None)))

    override final def commit: F[Unit] =
      transaction
        .commit()
        .toStream[S]
        .void[F]
        .guarantee(_ => lock.release)

    override final def rollback: F[Unit] =
      transaction
        .rollback()
        .toStream[S]
        .void[F]
        .guarantee(_ => lock.release)
  }
}
