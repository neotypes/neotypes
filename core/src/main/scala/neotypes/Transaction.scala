package neotypes

import java.util.{Map => JMap}
import java.util.concurrent.CompletionStage

import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam
import utils.traverse.{traverseAsList, traverseAsSet}
import utils.stage._

import org.neo4j.driver.v1.exceptions.NoSuchRecordException
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.{Record, StatementResultCursor, Value, Transaction => NTransaction}

import scala.collection.JavaConverters._

final class Transaction[F[_]](transaction: NTransaction)(implicit F: Async[F]) {
  import Transaction.{convertParams, recordToSeq}

  def execute[T](query: String, params: Map[String, QueryParam] = Map.empty)
                (implicit executionMapper: ExecutionMapper[T]): F[T] =
    F.async { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .compose { x: StatementResultCursor => x.consumeAsync() }
        .accept { result: ResultSummary => cb(executionMapper.to(result)) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def list[T](query: String, params: Map[String, QueryParam] = Map.empty)
             (implicit resultMapper: ResultMapper[T]): F[List[T]] =
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

  def set[T](query: String, params: Map[String, QueryParam] = Map.empty)
            (implicit resultMapper: ResultMapper[T]): F[Set[T]] =
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

  def single[T](query: String, params: Map[String, QueryParam] = Map.empty)
               (implicit resultMapper: ResultMapper[T]): F[T] =
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

  private[this] def nextAsyncToF[T](cs: CompletionStage[Record])
                                   (implicit resultMapper: ResultMapper[T]): F[Option[T]] =
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
                                   (implicit S: Stream.Aux[S, F]): S[T] =
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

  def commit(): F[Unit] =
    F.async{ cb =>
      transaction
        .commitAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }

  def rollback(): F[Unit] =
    F.async { cb =>
      transaction
        .rollbackAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex => cb(Left(ex)) }
    }
}

object Transaction {
  private def recordToSeq(record: Record): Seq[(String, Value)] =
    record.fields.asScala.map(p => p.key -> p.value)

  private def convertParams(params: Map[String, QueryParam]): JMap[String, Object] =
    params.mapValues(_.underlying).asJava
}
