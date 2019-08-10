package neotypes

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.higherKinds
import scala.util.{Failure, Success}

@implicitNotFound("The effect type ${F} is not supported by neotypes")
trait Async[F[_]] {
  type R[A]

  def async[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A]

  def delay[A](a: => A): F[A]

  def failed[A](e: Throwable): F[A]

  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  def map[A, B](fa: F[A])(f: A => B): F[B]

  def recoverWith[A, B >: A](fa: F[A])(f: PartialFunction[Throwable, F[B]]): F[B]

  def resource[A](input: => A)(close: A => F[Unit]): R[A]
}

object Async {
  type Aux[F[_], _R[_]] = Async[F] { type R[A] = _R[A] }

  private[neotypes] type Id[A] = A

  implicit def futureAsync(implicit ec: ExecutionContext): Async.Aux[Future, Id] =
    new Async[Future] {
      override final type R[A] = A

      override final def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
        val p = Promise[A]()
        cb {
          case Right(res) => p.complete(Success(res))
          case Left(ex)   => p.complete(Failure(ex))
        }
        p.future
      }

      override final def delay[A](a: => A): Future[A] =
        Future(a)

      override final def failed[A](e: Throwable): Future[A] =
        Future.failed(e)

      override final def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
        fa.flatMap(f)

      override final def map[A, B](fa: Future[A])(f: A => B): Future[B] =
        fa.map(f)

      override final def recoverWith[A, B >: A](fa: Future[A])(f: PartialFunction[Throwable, Future[B]]): Future[B] =
        fa.recoverWith(f)

      override final def resource[A](input: => A)(close: A => Future[Unit]): A =
        input
    }
}
