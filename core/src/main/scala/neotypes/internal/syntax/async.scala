package neotypes
package internal.syntax

private[neotypes] object async {
  implicit class AsyncOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit F: Async[F]): F[B] =
      F.map(fa)(f)

    def flatMap[B](f: A => F[B])(implicit F: Async[F]): F[B] =
      F.flatMap(fa)(f)

    def >>[B](fb: F[B])(implicit F: Async[F]): F[B] =
      F.flatMap(fa)(_ => fb)

    def guarantee[B](f: A => F[B])(finalizer: (A, Option[Throwable]) => F[Unit])(implicit F: Async[F]): F[B] =
      F.guarantee(fa)(f)(finalizer)

    def recover[B >: A](f: PartialFunction[Throwable, B])(implicit F: Async[F]): F[B] =
      F.recoverWith[A, B](fa)(f.andThen(b => F.delay(b)))

    def recoverWith[B >: A](f: PartialFunction[Throwable, F[B]])(implicit F: Async[F]): F[B] =
      F.recoverWith[A, B](fa)(f)
  }
}
