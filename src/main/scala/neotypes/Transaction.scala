package neotypes

import java.util

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
      .exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
  }

  def list[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[Seq[T]] = {
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
        ).exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
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
        .exceptionally { ex: Throwable =>
          if (ex.getClass == classOf[NoSuchRecordException] || ex.getCause.getClass == classOf[NoSuchRecordException]) {
            cb(resultMapper.to(Seq(), None)).asInstanceOf[Void]
          } else {
            cb(Left(ex)).asInstanceOf[Void]
          }
        }
    }
  }

  def commit(): F[Unit] = F.async(cb =>
    transaction.commitAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
  )

  def rollback(): F[Unit] = F.async(cb =>
    transaction.rollbackAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally { ex: Throwable => cb(Left(ex)).asInstanceOf[Void] }
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