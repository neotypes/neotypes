package neotypes

import internal.utils.traverse._
import internal.syntax.async._
import internal.syntax.stage._
import internal.syntax.stream._
import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import org.neo4j.driver.{Record, Value}
import org.neo4j.driver.async.{AsyncSession => NeoAsyncSession, AsyncTransaction => NeoAsyncTransaction}
import org.neo4j.driver.exceptions.NoSuchRecordException
import org.neo4j.driver.reactive.{RxSession => NeoRxSession, RxTransaction => NeoRxTransaction}

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

  def commit: F[Unit]

  def rollback: F[Unit]
}

sealed trait StreamingTransaction[S[_], F[_]] extends Transaction[F] {
  def stream[T](query: String, params: Map[String, QueryParam])
               (implicit resultMapper: ResultMapper[T]): S[T]
}

object Transaction {
  private def recordToList(record: Record): List[(String, Value)] =
    record.fields.asScala.iterator.map(p => p.key -> p.value).toList

  private[neotypes] def apply[F[_]](transaction: NeoAsyncTransaction, session: NeoAsyncSession)
                                   (implicit F: Async[F]): Transaction[F] =
    new Transaction[F] {
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

      override final def execute[T](query: String, params: Map[String,QueryParam])
                                   (implicit executionMapper: ExecutionMapper[T]): F[T] =
        F.async { cb =>
          transaction
            .runAsync(query, QueryParam.toJavaMap(params))
            .thenCompose(_.consumeAsync())
            .accept(cb)(executionMapper.to)
        }

      override final def collectAs[C, T](factory: Factory[T,C])
                                        (query: String, params: Map[String,QueryParam])
                                        (implicit resultMapper: ResultMapper[T]): F[C] =
        collectAsImpl(transaction, query, params)(traverseAs(factory))

      override final def list[T](query: String, params: Map[String,QueryParam])
                                (implicit resultMapper: ResultMapper[T]): F[List[T]] =
        collectAsImpl(transaction, query, params)(traverseAsList[Record, T])

      override final def map[K, V](query: String, params: Map[String,QueryParam])
                                  (implicit resultMapper: ResultMapper[(K, V)]): F[Map[K,V]] =
        collectAsImpl(transaction, query, params)(traverseAsMap[Record, K, V])

      override final def set[T](query: String, params: Map[String,QueryParam])
                               (implicit resultMapper: ResultMapper[T]): F[Set[T]] =
        collectAsImpl(transaction, query, params)(traverseAsSet[Record, T])

      override final def vector[T](query: String, params: Map[String,QueryParam])
                                  (implicit resultMapper: ResultMapper[T]): F[Vector[T]] =
        collectAsImpl(transaction, query, params)(traverseAsVector[Record, T])

      override final def single[T](query: String, params: Map[String,QueryParam])
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

      private def closeSession: F[Unit] =
        F.async { cb =>
          session.closeAsync().acceptVoid(cb)
        }

      override final def commit: F[Unit] =
        F.async[Unit] { cb =>
          transaction.commitAsync().acceptVoid(cb)
        } guarantee { _ =>
          closeSession
        }

      override final def rollback: F[Unit] =
        F.async[Unit] { cb =>
          transaction.rollbackAsync().acceptVoid(cb)
        } guarantee { _ =>
          closeSession
        }
    }

  private[neotypes] def apply[S[_], F[_]](transaction: NeoRxTransaction, session: NeoRxSession)
                                         (implicit S: Stream.Aux[S, F], F: Async[F]): StreamingTransaction[S, F] =
    new StreamingTransaction[S, F] {
      override final def execute[T](query: String, params: Map[String, QueryParam])
                                   (implicit executionMapper: ExecutionMapper[T]): F[T] = {
        val rxResult = transaction.run(query, QueryParam.toJavaMap(params))

        for {
          // Ensure to process all records before asking for the result summary.
          _ <- rxResult.records.toStream[S].void[F]
          r <- rxResult.consume.toStream[S].single[F]
          t <- F.fromEither(r.toRight(left = exceptions.ConversionException("Empty result summary")).flatMap(executionMapper.to))
        } yield t
      }

      override final def collectAs[C, T](factory: Factory[T, C])
                                        (query: String, params: Map[String, QueryParam])
                                        (implicit resultMapper: ResultMapper[T]): F[C] =
        stream(query, params).collectAs[F, C](factory)

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
        stream(query, params).single[F].flatMap {
          case Some(a) => F.delay(a)
          case None    => F.fromEither(resultMapper.to(List.empty, None))
        }

      override final def stream[T](query: String, params: Map[String, QueryParam])
                                  (implicit resultMapper: ResultMapper[T]): S[T] =
        transaction
          .run(query, QueryParam.toJavaMap(params))
          .records
          .toStream[S]
          .evalMap(r => F.fromEither(resultMapper.to(recordToList(r), None)))

      private def closeSession: F[Unit] =
        session.close[Unit].toStream[S].void

      override final def commit: F[Unit] =
        transaction
          .commit[Unit]
          .toStream[S]
          .void[F]
          .guarantee(_ => closeSession)

      override final def rollback: F[Unit] =
        transaction
          .rollback[Unit]
          .toStream[S]
          .void[F]
          .guarantee(_ => closeSession)
    }
}
