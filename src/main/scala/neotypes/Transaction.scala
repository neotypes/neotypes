package neotypes

import java.util

import neotypes.mappers.{ExecutionMapper, ResultMapper}
import org.neo4j.driver.v1.summary.ResultSummary
import org.neo4j.driver.v1.{Record, Transaction => NTransaction}

import scala.collection.JavaConverters._

class Transaction[F[+ _]](transaction: NTransaction)(implicit F: Async[F]) {

  def execute[T: ExecutionMapper](query: String, params: Map[String, Any] = Map()): F[T] = F.async[T] { cb =>
    val executionMapper = implicitly[ExecutionMapper[T]]

    transaction
      .runAsync(query, params.asJava.asInstanceOf[util.Map[String, Object]])
      .thenCompose(_.consumeAsync())
      .thenAccept((result: ResultSummary) => cb(executionMapper.to(result)))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
  }

  def list[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[Seq[T]] = {
    val resultMapper = implicitly[ResultMapper[T]]

    F.async[Seq[T]] { cb =>
      transaction
        .runAsync(query, params.asJava.asInstanceOf[util.Map[String, Object]])
        .thenCompose(_.listAsync())
        .thenAccept((result: util.List[Record]) =>
          cb {
            val list = result.asScala
              .map(_.fields().asScala.map(p => p.key() -> p.value()))
              .map(resultMapper.to)

            list.find(_.isLeft).map(_.asInstanceOf[Either[Exception, Seq[T]]]).getOrElse(Right(list.collect { case Right(v) => v }))
          }
        ).exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def single[T: ResultMapper](query: String, params: Map[String, Any] = Map()): F[T] = {
    val resultMapper = implicitly[ResultMapper[T]]

    F.async[T] { cb =>
      transaction.runAsync(query, params.asJava.asInstanceOf[util.Map[String, Object]])
        .thenCompose(_.singleAsync())
        .thenAccept((res: Record) => cb {
          resultMapper.to(res.fields().asScala.map(p => p.key() -> p.value()))
        })
        .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def commit(): F[Unit] = F.async(cb =>
    transaction.commitAsync()
      .thenAccept(_ => cb(Right(())))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
  )

  def rollback(): F[Unit] = F.async(cb =>
    transaction.rollbackAsync()
      .thenAccept(_ => cb(Right(())))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
  )
}
