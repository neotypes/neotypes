package neotypes

import java.util.concurrent.CompletionStage
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.util.{Failure, Success}

@annotation.implicitNotFound("The effect type ${F} is not supported by neotypes")
trait Async[F[_]] {
  private[neotypes] type R[A]

  private[neotypes] def fromCompletionStage[A](completionStage: => CompletionStage[A]): F[A]

  private[neotypes] def delay[A](a: => A): F[A]

  private[neotypes] def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  private[neotypes] def fromEither[A](e: => Either[Throwable, A]): F[A]

  private[neotypes] def guarantee[A, B](fa: F[A])
                                       (f: A => F[B])
                                       (finalizer: (A, Option[Throwable]) => F[Unit]): F[B]

  private[neotypes] def map[A, B](fa: F[A])(f: A => B): F[B]

  private[neotypes] def mapError[A](fa: F[A])(f: Throwable => Throwable): F[A]

  private[neotypes] def resource[A](a: => A)(close: A => F[Unit]): R[A]
}

object Async {
  type Aux[F[_], _R[_]] = Async[F] { type R[A] = _R[A] }
  private[neotypes] type Id[A] = A

  implicit def futureAsync(implicit ec: ExecutionContext): Async.Aux[Future, Id] =
    new Async[Future] {
      override final type R[A] = A

      override final def fromCompletionStage[A](completionStage: => CompletionStage[A]): Future[A] =
        Future(completionStage).flatMap(_.asScala)

      override final def delay[A](a: => A): Future[A] =
        Future(a)

      override final def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
        fa.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable,A]): Future[A] =
        Future.fromTry(e.toTry)

      override final def guarantee[A, B](fa: Future[A])
                                        (f: A => Future[B])
                                        (finalizer: (A, Option[Throwable]) => Future[Unit]): Future[B] =
        fa.flatMap { a =>
          f(a).transformWith {
            case Success(b)  => finalizer(a, None).map(_ => b)
            case Failure(ex) => finalizer(a, Some(ex)).transform(_ => Failure(ex))
          }
        }

      override final def map[A, B](fa: Future[A])(f: A => B): Future[B] =
        fa.map(f)

      override final def mapError[A](fa: Future[A])(f: Throwable => Throwable): Future[A] =
        fa.transform(identity, f)

      override final def resource[A](a: => A)(close: A => Future[Unit]): A =
        a
    }
}
