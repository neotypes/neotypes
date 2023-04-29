package neotypes

import internal.utils.traverseAs
import internal.syntax.async._
import internal.syntax.stream._
import mappers.ResultMapper
import model.exceptions.MissingRecordException
import model.query.QueryParam

import org.neo4j.driver.async.{ResultCursor => NeoAsyncResult, AsyncTransaction => NeoAsyncTransaction}
import org.neo4j.driver.reactive.{ReactiveResult => NeoReactiveResult, ReactiveTransaction => NeoReactiveTransaction}
import org.neo4j.driver.summary.ResultSummary

import scala.collection.Factory
import scala.jdk.CollectionConverters._

sealed trait AsyncTransaction[F[_]] {
  private[neotypes] def F: Async[F]

  def commit: F[Unit]
  def rollback: F[Unit]

  def execute(
    query: String,
    params: Map[String, QueryParam]
  ): F[ResultSummary]

  def single[T](
    query: String,
    params: Map[String, QueryParam],
    mapper: ResultMapper[T]
  ): F[(T, ResultSummary)]

  def collectAs[T, C](
    query: String,
    params: Map[String, QueryParam],
    mapper: ResultMapper[T],
    factory: Factory[T, C]
  ): F[(C, ResultSummary)]
}

sealed trait StreamTransaction[S[_], F[_]] extends AsyncTransaction[F] {
  private[neotypes] def S: Stream.Aux[S, F]

  def stream[T](
    query: String,
    params: Map[String, QueryParam],
    mapper: ResultMapper[T],
    chunkSize: Int
  ): S[Either[T, ResultSummary]]
}

object Transaction {
  private[neotypes] def async[F[_]](
      transaction: NeoAsyncTransaction,
      close: () => F[Unit]
  ) (
      implicit evF: Async[F]
  ): AsyncTransaction[F] =
    new AsyncTransaction[F] {
      override final val F: Async[F] = evF

      override final def commit: F[Unit] =
        F.fromCompletionStage(transaction.commitAsync()).void.guarantee(_ => close())

      override final def rollback: F[Unit] =
        F.fromCompletionStage(transaction.rollbackAsync()).void.guarantee(_ => close())

      private def runQuery(query: String, params: Map[String, QueryParam]): F[NeoAsyncResult] =
        F.fromCompletionStage(
          transaction.runAsync(query, QueryParam.toJavaMap(params))
        ).mapError(_ => MissingRecordException)

      private def resultSummary(result: NeoAsyncResult): F[ResultSummary] =
        F.fromCompletionStage(result.consumeAsync()).mapError(_ => MissingRecordException)

      override final def execute(
        query: String,
        params: Map[String, QueryParam]
      ): F[ResultSummary] =
        runQuery(query, params).flatMap(resultSummary)

      override final def single[T](
        query: String,
        params: Map[String, QueryParam],
        mapper: ResultMapper[T]
      ): F[(T, ResultSummary)] =
        for {
          result <- runQuery(query, params)
          record <- F.fromCompletionStage(result.singleAsync()).mapError(_ => MissingRecordException)
          t <- F.fromEither(Parser.decodeRecord(record, mapper))
          rs <- resultSummary(result)
        } yield t -> rs

      override final def collectAs[T, C](
        query: String,
        params: Map[String, QueryParam],
        mapper: ResultMapper[T],
        factory: Factory[T, C]
      ): F[(C, ResultSummary)] =
        for {
          result <- runQuery(query, params)
          records <- F.fromCompletionStage(result.listAsync())
          col <- F.fromEither(traverseAs(factory)(records.asScala) { record =>
            Parser.decodeRecord(record, mapper)
          })
          rs <- resultSummary(result)
        } yield col -> rs
    }

  private[neotypes] def stream[S[_], F[_]](
      transaction: NeoReactiveTransaction,
      close: () => F[Unit]
  ) (
      implicit evS: Stream.Aux[S, F], evF: Async[F]
  ): StreamTransaction[S, F] =
    new StreamTransaction[S, F] {
      override final val F: Async[F] = evF
      override final val S: Stream.Aux[S, F] = evS

      override final def commit: F[Unit] =
        S.fromPublisher(transaction.commit[Unit]).voidS[F].guarantee(_ => close())

      override final def rollback: F[Unit] =
        S.fromPublisher(transaction.rollback[Unit]).voidS[F].guarantee(_ => close())

      private def runQuery(query: String, params: Map[String, QueryParam]): F[NeoReactiveResult] =
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).single[F].flatMap { result =>
          F.fromEither(result.toRight(left = MissingRecordException))
        }

      private def resultSummary(result: NeoReactiveResult): F[ResultSummary] =
        S.fromPublisher(result.consume()).single[F].flatMap {
          case Some(rs) =>
            F.delay(rs)

          case None =>
            F.fromEither(Left(MissingRecordException))
        }

      override final def execute(
        query: String,
        params: Map[String, QueryParam]
      ): F[ResultSummary] =
        for {
          result <- runQuery(query, params)
          _ <- S.fromPublisher(result.records()).voidS[F]
          rs <- resultSummary(result)
        } yield rs

      override final def single[T](
        query: String,
        params: Map[String, QueryParam],
        mapper: ResultMapper[T]
      ): F[(T, ResultSummary)] =
        for {
          result <- runQuery(query, params)
          recordOpt <- S.fromPublisher(result.records()).single[F]
          record <- F.fromEither(recordOpt.toRight(left = MissingRecordException))
          t <- F.fromEither(Parser.decodeRecord(record, mapper))
          rs <- resultSummary(result)
        } yield t -> rs

      override final def collectAs[T, C](
        query: String,
        params: Map[String, QueryParam],
        mapper: ResultMapper[T],
        factory: Factory[T, C]
      ): F[(C, ResultSummary)] =
        for {
          result <- runQuery(query, params)
          records = S.fromPublisher(result.records(), chunkSize = 256).evalMap { record =>
            F.fromEither(Parser.decodeRecord(record, mapper))
          }
          col <- records.collectAs(factory)
          rs <- resultSummary(result)
        } yield col -> rs

      override final def stream[T](
        query: String,
        params: Map[String, QueryParam],
        mapper: ResultMapper[T],
        chunkSize: Int
      ): S[Either[T, ResultSummary]] =
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).flatMapS { result =>
          val records = S.fromPublisher(result.records(), chunkSize).evalMap { record =>
            F.fromEither(Parser.decodeRecord(record, mapper))
          }

          val summary = S.fromPublisher(result.consume())

          records andThen summary
        }
    }
}
