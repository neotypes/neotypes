package neotypes

import java.util.{Map => JMap}
import java.util.concurrent.CompletionStage

import internal.utils.traverse.{traverseAs, traverseAsList, traverseAsMap, traverseAsSet, traverseAsVector}
import internal.syntax.stage._
import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import org.neo4j.driver.v1.exceptions.NoSuchRecordException
import org.neo4j.driver.v1.{Record, StatementResultCursor, Transaction => NTransaction, Value}

import scala.collection.compat._
import scala.jdk.CollectionConverters._

final class Transaction[F[_]](val transaction: NTransaction) extends AnyVal {
  import Transaction.{collectAsImpl, convertParams, nextAsyncToF, recordToSeq}

  def execute[T](query: String, params: Map[String, QueryParam] = Map.empty)
                (implicit F: Async[F], executionMapper: ExecutionMapper[T]): F[T] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .thenCompose(_.consumeAsync())
        .accept(cb)(executionMapper.to)
    }

  def collectAs[C, T](factory: Factory[T, C])
                     (query: String, params: Map[String, QueryParam] = Map.empty)
                     (implicit F: Async[F], resultMapper: ResultMapper[T]): F[C] =
    collectAsImpl(this.transaction)(query, params)(traverseAs(factory))

  def list[T](query: String, params: Map[String, QueryParam] = Map.empty)
             (implicit F: Async[F], resultMapper: ResultMapper[T]): F[List[T]] =
    collectAsImpl(this.transaction)(query, params)(traverseAsList[Record, T])

  def map[K, V](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[(K, V)]): F[Map[K, V]] =
    collectAsImpl(this.transaction)(query, params)(traverseAsMap[Record, K, V])

  def set[T](query: String, params: Map[String, QueryParam] = Map.empty)
            (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Set[T]] =
    collectAsImpl(this.transaction)(query, params)(traverseAsSet[Record, T])

  def vector[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Vector[T]] =
    collectAsImpl(this.transaction)(query, params)(traverseAsVector[Record, T])

  def single[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[T]): F[T] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .thenCompose(_.singleAsync())
        .acceptExceptionally(cb) { res: Record =>
          resultMapper.to(recordToSeq(res), None)
        } {
          case _: NoSuchRecordException => resultMapper.to(Seq.empty, None)
        }
    }

  def stream[T: ResultMapper, S[_]](query: String, params: Map[String, QueryParam] = Map.empty)
                                   (implicit F: Async[F], S: Stream.Aux[S, F]): S[T] =
    S.fToS(
      F.async { cb =>
        transaction
          .runAsync(query, convertParams(params))
          .accept(cb) { x: StatementResultCursor =>
            Right(S.init(() => nextAsyncToF(x.nextAsync())))
          }
      }
    )

  def commit(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      transaction.commitAsync().acceptVoid(cb)
    }

  def rollback(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      transaction.rollbackAsync().acceptVoid(cb)
    }
}

object Transaction {
  private def recordToSeq(record: Record): Seq[(String, Value)] =
    record.fields.asScala.view.map(p => p.key -> p.value).to(Seq)

  private def convertParams(params: Map[String, QueryParam]): JMap[String, Object] =
    params.view.mapValues(_.underlying).toMap.asJava

  private def nextAsyncToF[F[_], T](cs: CompletionStage[Record])
                                   (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Option[T]] =
    F.async { cb =>
      cs.accept(cb) { res: Record =>
        Option(res) match {
          case None =>
            Right(None)

          case Some(res) =>
            resultMapper.to(recordToSeq(res), None).map(r => Option(r))
        }
      }
    }

  private def collectAsImpl[F[_], T, C](transaction: NTransaction)
                                       (query: String, params: Map[String, QueryParam])
                                       (traverseFun: Iterator[Record] => (Record => Either[Throwable, T]) => Either[Throwable, C])
                                       (implicit F: Async[F], resultMapper: ResultMapper[T]): F[C] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .thenCompose(_.listAsync())
        .accept(cb) { result: java.util.List[Record] =>
          traverseFun(result.asScala.iterator) { v: Record =>
            resultMapper.to(recordToSeq(v), None)
          }
        }
    }
}
