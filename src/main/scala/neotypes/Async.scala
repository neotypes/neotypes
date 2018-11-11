package neotypes

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait Async[F[+ _]] {
  def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T]

  def flatMap[T, U](m: F[T])(f: T => F[U]): F[U]

  def map[T, U](m: F[T])(f: T => U): F[U]

  def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U]

  def failed[T](e: Throwable): F[T]
}

object Async {

  implicit class AsyncExt[F[+ _], +T](m: F[T])(implicit F: Async[F]) {
    def map[U](f: T => U) = F.map(m)(f)

    def flatMap[U](f: T => F[U]) = F.flatMap(m)(f)

    def recoverWith[U >: T](f: PartialFunction[Throwable, F[U]]): F[U] = F.recoverWith[T, U](m)(f)
  }

  class FutureAsync(implicit ec: ExecutionContext) extends Async[Future] {

    override def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
      val p = scala.concurrent.Promise[A]
      cb {
        case Right(res) => p.complete(Success(res))
        case Left(ex) => p.complete(Failure(ex))
      }
      p.future
    }

    override def flatMap[T, U](m: Future[T])(f: T => Future[U]): Future[U] = m.flatMap(f)

    override def map[T, U](m: Future[T])(f: T => U): Future[U] = m.map(f)

    override def recoverWith[T, U >: T](m: Future[T])(f: PartialFunction[Throwable, Future[U]]): Future[U] = m.recoverWith(f)

    override def failed[T](e: Throwable): Future[T] = Future.failed(e)
  }

  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] = new FutureAsync
}
