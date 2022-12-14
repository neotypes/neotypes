package neotypes

import internal.utils.{parseRecord, traverseAs}
import internal.syntax.async._
import internal.syntax.stream._
import mappers.ResultMapper
import model.QueryParam
import model.exceptions.MissingRecordException

import org.neo4j.driver.async.{AsyncTransaction => NeoAsyncTransaction}
import org.neo4j.driver.reactive.{ReactiveResult, ReactiveTransaction => NeoReactiveTransaction}
import org.neo4j.driver.summary.ResultSummary

import scala.collection.Factory
import scala.jdk.CollectionConverters._

sealed trait Transaction[F[_]] {
  def commit: F[Unit]
  def rollback: F[Unit]

  def execute(
    query: String,
    params: Map[String, QueryParam]
  ): F[ResultSummary]

  def single[T](
    query: String,
    params: Map[String, QueryParam],
    resultMapper: ResultMapper[T]
  ): F[(T, ResultSummary)]

  def collectAs[T, C](
    query: String,
    params: Map[String, QueryParam],
    factory: Factory[T, C],
    resultMapper: ResultMapper[T]
  ): F[(C, ResultSummary)]
}

sealed trait StreamingTransaction[S[_], F[_]] extends Transaction[F] {
  def stream[T](
    query: String,
    params: Map[String, QueryParam],
    resultMapper: ResultMapper[T]
  ): S[Either[T, ResultSummary]]
}

object Transaction {
  private[neotypes] def apply[F[_]](transaction: NeoAsyncTransaction, close: () => F[Unit])
                                   (implicit F: Async[F]): Transaction[F] =
    new Transaction[F] {
      override final def commit: F[Unit] =
        F.fromCompletionStage(transaction.commitAsync()).void.guarantee(_ => close())

      override final def rollback: F[Unit] =
        F.fromCompletionStage(transaction.rollbackAsync()).void.guarantee(_ => close())

      override final def execute(
        query: String,
        params: Map[String,QueryParam]
      ): F[ResultSummary] =
        F.fromCompletionStage(transaction.runAsync(query, QueryParam.toJavaMap(params))).flatMap { result =>
          F.fromCompletionStage(result.consumeAsync())
        }

      override final def single[T](
        query: String,
        params: Map[String,QueryParam],
        resultMapper: ResultMapper[T]
      ): F[(T, ResultSummary)] =
        F.fromCompletionStage(transaction.runAsync(query, QueryParam.toJavaMap(params))).flatMap { result =>
          F.fromCompletionStage(result.singleAsync()).flatMap { record =>
            F.fromCompletionStage(result.consumeAsync()).flatMap { rs =>
              F.fromEither(resultMapper.decode(parseRecord(record))).map(t => t -> rs)
            }
          }
        }

      override final def collectAs[T, C](
        query: String,
        params: Map[String, QueryParam],
        factory: Factory[T, C],
        resultMapper: ResultMapper[T]
      ): F[(C, ResultSummary)] =
        F.fromCompletionStage(transaction.runAsync(query, QueryParam.toJavaMap(params))).flatMap { result =>
          F.fromCompletionStage(result.listAsync()).flatMap { records =>
            F.fromCompletionStage(result.consumeAsync()).flatMap { rs =>
              F.fromEither(traverseAs(factory)(records.asScala.iterator) { record =>
                resultMapper.decode(parseRecord(record))
              }).map(col => col -> rs)
            }
          }
        }
    }

  private[neotypes] def apply[S[_], F[_]](transaction: NeoReactiveTransaction, close: () => F[Unit])
                                         (implicit S: Stream.Aux[S, F], F: Async[F]): StreamingTransaction[S, F] =
    new StreamingTransaction[S, F] {
      override final def commit: F[Unit] =
        S.fromPublisher(transaction.commit[Unit]).voidS[F].guarantee(_ => close())

      override final def rollback: F[Unit] =
        S.fromPublisher(transaction.rollback[Unit]).voidS[F].guarantee(_ => close())

      private def resultSummary(result: ReactiveResult): F[ResultSummary] =
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
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).single[F].flatMap {
          case Some(result) =>
            S.fromPublisher(result.records()).voidS[F].flatMap { _ =>
              resultSummary(result)
            }

          case None =>
            F.fromEither(Left(MissingRecordException))
        }

      override final def single[T](
        query: String,
        params: Map[String, QueryParam],
        resultMapper: ResultMapper[T]
      ): F[(T, ResultSummary)] =
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).single[F].flatMap {
          case Some(result) =>
            S.fromPublisher(result.records()).single[F].flatMap {
              case Some(record) =>
                F.fromEither(resultMapper.decode(parseRecord(record))).flatMap { t =>
                  resultSummary(result).map(rs => t -> rs)
                }

              case None =>
                F.fromEither(Left(MissingRecordException))
            }

          case None =>
            F.fromEither(Left(MissingRecordException))
        }

      override final def collectAs[T, C](
        query: String,
        params: Map[String, QueryParam],
        factory: Factory[T, C],
        resultMapper: ResultMapper[T]
      ): F[(C, ResultSummary)] =
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).single[F].flatMap {
          case Some(result) =>
            val records = S.fromPublisher(result.records()).evalMap { record =>
              F.fromEither(resultMapper.decode(parseRecord(record)))
            }

            records.collectAs(factory).flatMap { col =>
              resultSummary(result).map(rs => col -> rs)
            }

          case None =>
            F.fromEither(Left(MissingRecordException))
        }

      override final def stream[T](
        query: String,
        params: Map[String, QueryParam],
        resultMapper: ResultMapper[T]
      ): S[Either[T, ResultSummary]] =
        S.fromPublisher(transaction.run(query, QueryParam.toJavaMap(params))).flatMapS { result =>
          val records = S.fromPublisher(result.records()).evalMap { record =>
            F.fromEither(resultMapper.decode(parseRecord(record)))
          }

          val summary = S.fromPublisher(result.consume())

          records andThen summary
        }
    }
}
