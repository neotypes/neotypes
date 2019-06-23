package neotypes

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.higherKinds
import scala.util.{Failure, Success}

trait Async[F[_]] {
  def async[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A]

  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]

  def map[A, B](m: F[A])(f: A => B): F[B]

  def recoverWith[A, B >: A](m: F[A])(f: PartialFunction[Throwable, F[B]]): F[B]

  def failed[A](e: Throwable): F[A]

  def success[A](t: => A): F[A]
}

object Async {
  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] =
    new Async[Future] {
      override def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
        val p = Promise[A]()
        cb {
          case Right(res) => p.complete(Success(res))
          case Left(ex)   => p.complete(Failure(ex))
        }
        p.future
      }

      override def flatMap[A, B](m: Future[A])(f: A => Future[B]): Future[B] =
        m.flatMap(f)

      override def map[A, B](m: Future[A])(f: A => B): Future[B] =
        m.map(f)

      override def recoverWith[A, B >: A](m: Future[A])(f: PartialFunction[Throwable, Future[B]]): Future[B] =
        m.recoverWith(f)

      override def failed[A](e: Throwable): Future[A] =
        Future.failed(e)

      override def success[A](t: => A): Future[A] =
        Future.successful(t)
    }
}
