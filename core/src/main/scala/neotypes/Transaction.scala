package neotypes

import java.util
import java.util.concurrent.CompletionStage

import neotypes.Transaction.convertParams
import neotypes.mappers.{ExecutionMapper, ResultMapper}
import neotypes.utils.FunctionUtils._
import org.neo4j.driver.v1.exceptions.NoSuchRecordException
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.{Record, StatementResultCursor, Value, Transaction => NTransaction}

import scala.collection.JavaConverters._

class Transaction[F[_]](transaction: NTransaction)(implicit F: Async[F]) {

  def execute[T: ExecutionMapper](query: String, params: Map[String, Any] = Map()): F[T] = F.async[T] { cb =>
    val executionMapper = implicitly[ExecutionMapper[T]]

    transaction
      .runAsync(query, convertParams(params))
      .thenCompose { x: StatementResultCursor => x.consumeAsync() }
      .thenAccept((result: ResultSummary) => cb(executionMapper.to(result)))
      .exceptionally(exceptionally(e => cb(Left(e))))
  }

  def list[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[List[T]] =
    F.map(seq(query, params))(_.toList)

  def seq[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[Seq[T]] = {
    val resultMapper = implicitly[ResultMapper[T]]

    F.async[Seq[T]] { cb =>
      transaction
        .runAsync(query, convertParams(params))
        .thenCompose { x: StatementResultCursor => x.listAsync() }
        .thenAccept((result: util.List[Record]) =>
          cb {
            val list = result.asScala.map(v => resultMapper.to(recordToSeq(v), None))

            list.find(_.isLeft).map(_.asInstanceOf[Either[Exception, Seq[T]]]).getOrElse(Right(list.collect { case Right(v) => v }))
          }
        ).exceptionally(exceptionally(e => cb(Left(e))))
    }
  }

  def single[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[T] = {
    val resultMapper = implicitly[ResultMapper[T]]

    F.async[T] { cb =>
      transaction.runAsync(query, convertParams(params))
        .thenCompose((x: StatementResultCursor) => x.singleAsync())
        .thenAccept((res: Record) => cb {
          resultMapper.to(recordToSeq(res), None)
        })
        .exceptionally(exceptionally {
          case _: NoSuchRecordException =>
            cb(resultMapper.to(Seq(), None))
          case ex: Throwable =>
            cb(Left(ex))
        })
    }
  }

  private[this] def nextAsyncToF[T: ResultMapper](cs: CompletionStage[Record]): F[T] = {
    val resultMapper = implicitly[ResultMapper[T]]

    F.async[T] { cb =>
      cs.thenAccept { res: Record =>
        cb(if (res == null) Right[Throwable, T](null.asInstanceOf[T]) else resultMapper.to(recordToSeq(res), None))
      }.exceptionally(exceptionally { ex => cb(Left(ex)) })
    }
  }

  def stream[T: ResultMapper, S[_]](query: String, params: Map[String, Any] = Map())(implicit S: Stream[S, F]): S[T] = {
    S.fToS(
      F.async[S[T]] { cb =>
        transaction.runAsync(query, convertParams(params))
          .thenAccept { (x: StatementResultCursor) =>
            cb {
              Right(S.init[T](() => F.map(nextAsyncToF(x.nextAsync()))(Option(_))))
            }
          }
          .exceptionally(exceptionally(ex =>
            cb(Left(ex))
          ))
      }
    )
  }

  def commit(): F[Unit] = F.async(cb =>
    transaction.commitAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally(exceptionally(ex => cb(Left(ex))))
  )

  def rollback(): F[Unit] = F.async(cb =>
    transaction.rollbackAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally(exceptionally(ex => cb(Left(ex))))
  )

  private[this] def recordToSeq(record: Record): Seq[(String, Value)] = record.fields().asScala.map(p => p.key() -> p.value())
}

object Transaction {
  def convertParams(params: Map[String, Any]): util.Map[String, Object] = {
    params.map {
      case (k, v) => k -> toNeoType(v)
    }.asJava.asInstanceOf[util.Map[String, Object]]
  }

  def toNeoType(value: Any): Any = value match {
    case s: Seq[Any] => new util.ArrayList[Any](s.map(toNeoType).asJava)
    case m: Map[Any, Any] => new util.HashMap[Any, Any](m.mapValues(toNeoType).asJava)
    case Some(v) => v
    case None => null
    case v: Any => v
  }
}
