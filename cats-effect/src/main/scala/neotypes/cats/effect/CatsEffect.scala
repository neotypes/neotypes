package neotypes.cats.effect

import neotypes.exceptions.CancellationException

import cats.effect.{Async, Resource}

trait CatsEffect {
  private[neotypes] final type FResource[F[_]] = { type R[A] = Resource[F, A] }

  implicit final def catsAsync[F[_]](implicit F: Async[F]): neotypes.Async.Aux[F, FResource[F]#R] =
    new neotypes.Async[F] {
      override final type R[A] = Resource[F, A]

      override final def async[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A] =
        F.async_(cb)

      override final def delay[A](t: => A): F[A] =
        F.delay(t)

      override final def flatMap[A, B](m: F[A])(f: A => F[B]): F[B] =
        F.flatMap(m)(f)

      override final def fromEither[A](e: => Either[Throwable, A]): F[A] =
        F.defer(F.fromEither(e))

      override final def guarantee[A, B](fa: F[A])
                                        (f: A => F[B])
                                        (finalizer: (A, Option[Throwable]) => F[Unit]): F[B] =
        Resource.makeCase(fa) {
          case (a, Resource.ExitCase.Succeeded)   => finalizer(a, None)
          case (a, Resource.ExitCase.Canceled)    => finalizer(a, Some(CancellationException))
          case (a, Resource.ExitCase.Errored(ex)) => finalizer(a, Some(ex))
        }.use(f)

      override final def map[A, B](m: F[A])(f: A => B): F[B] =
        F.map(m)(f)

      override final def resource[A](input: => A)(close: A => F[Unit]): Resource[F, A] =
        Resource.make(delay(input))(close)
    }
}
