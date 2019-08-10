package neotypes

import java.util.{Map => JMap}
import java.util.concurrent.CompletionStage

import internal.utils.traverse.{traverseAsList, traverseAsMap, traverseAsSet, traverseAsVector}
import internal.syntax.stage._
import mappers.{ExecutionMapper, ResultMapper, TypeHint}
import types.QueryParam

import org.neo4j.driver.v1.exceptions.NoSuchRecordException
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.{Record, StatementResultCursor, Value, Transaction => NTransaction}

import scala.collection.JavaConverters._
import scala.language.higherKinds

final class Transaction[F[_]](private val transaction: NTransaction) extends AnyVal {
  import Transaction.{convertParams, recordToSeq}

  def execute[T](query: String, params: Map[String, QueryParam] = Map.empty)
                (implicit F: Async[F], executionMapper: ExecutionMapper[T]): F[T] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.consumeAsync() }
        .accept { result: ResultSummary => cb(executionMapper.to(result)) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def list[T](query: String, params: Map[String, QueryParam] = Map.empty)
             (implicit F: Async[F], resultMapper: ResultMapper[T]): F[List[T]] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.listAsync() }
        .accept { result: java.util.List[Record] =>
          cb(
            traverseAsList(result.asScala.iterator) { v: Record =>
              resultMapper.to(recordToSeq(v), None)
            }
          )
        }.recover { ex: Throwable => cb(Left(ex)) }
    }

  def map[K, V](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[(K, V)]): F[Map[K, V]] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.listAsync() }
        .accept { result: java.util.List[Record] =>
          cb(
            traverseAsMap(result.asScala.iterator) { v: Record =>
              resultMapper.to(recordToSeq(v), Some(new TypeHint(true)))
            }
          )
        }.recover { ex: Throwable => cb(Left(ex)) }
    }

  def set[T](query: String, params: Map[String, QueryParam] = Map.empty)
            (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Set[T]] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.listAsync() }
        .accept { result: java.util.List[Record] =>
          cb(
            traverseAsSet(result.asScala.iterator) { v: Record =>
              resultMapper.to(recordToSeq(v), None)
            }
          )
        }.recover { ex: Throwable => cb(Left(ex)) }
    }

  def vector[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Vector[T]] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.listAsync() }
        .accept { result: java.util.List[Record] =>
          cb(
            traverseAsVector(result.asScala.iterator) { v: Record =>
              resultMapper.to(recordToSeq(v), None)
            }
          )
        }.recover { ex: Throwable => cb(Left(ex)) }
    }

  def single[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit F: Async[F], resultMapper: ResultMapper[T]): F[T] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.singleAsync() }
        .accept { res: Record => cb(resultMapper.to(recordToSeq(res), None)) }
        .recover {
          case _: NoSuchRecordException => cb(resultMapper.to(Seq.empty, None))
          case ex: Throwable            => cb(Left(ex))
        }
    }

  private def nextAsyncToF[T](cs: CompletionStage[Record])
                             (implicit F: Async[F], resultMapper: ResultMapper[T]): F[Option[T]] =
    F.async { cb =>
      cs.accept { res: Record =>
        cb(
          Option(res) match {
            case None =>
              Right(None)

            case Some(res) =>
              resultMapper
                .to(recordToSeq(res), None)
                .right
                .map(t => Some(t))
          }
        )
      }.recover { ex: Throwable => cb(Left(ex)) }
    }

  def stream[T: ResultMapper, S[_]](query: String, params: Map[String, QueryParam] = Map.empty)
                                   (implicit F: Async[F], S: Stream.Aux[S, F]): S[T] =
    S.fToS(
      F.async { cb =>
        transaction
          .runAsync(query, convertParams(params))
          .accept { x: StatementResultCursor =>
            cb(
              Right(
                S.init(
                  () => nextAsyncToF(x.nextAsync())
                )
              )
            )
          }.recover { ex: Throwable => cb(Left(ex)) }
      }
    )

  def commit(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      transaction
        .commitAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def rollback(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      transaction
        .rollbackAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }
}

object Transaction {
  private def recordToSeq(record: Record): Seq[(String, Value)] =
    record.fields.asScala.map(p => p.key -> p.value)

  private def convertParams(params: Map[String, QueryParam]): JMap[String, Object] =
    params.mapValues(_.underlying).asJava
}
