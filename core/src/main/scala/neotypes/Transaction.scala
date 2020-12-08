package neotypes

import internal.utils.traverse._
import internal.syntax.async._
import internal.syntax.stage._
import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import org.neo4j.driver.{Record, Value}
import org.neo4j.driver.async.{AsyncTransaction => NeoAsyncTransaction}
import org.neo4j.driver.exceptions.NoSuchRecordException

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

object Transaction {
  private[neotypes] def apply[F[_]](F: Async[F], transaction: NeoAsyncTransaction)
                                   (lock: F.Lock): Transaction[F] = new Transaction[F] {
    private implicit final val FF: Async[F] = F

    private def recordToList(record: Record): List[(String, Value)] =
      record.fields.asScala.iterator.map(p => p.key -> p.value).toList

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
                                 (implicit executionMapper: mappers.ExecutionMapper[T]): F[T] =
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
}
