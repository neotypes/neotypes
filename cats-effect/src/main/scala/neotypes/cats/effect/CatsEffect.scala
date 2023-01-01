package neotypes.cats.effect

import neotypes.model.exceptions.CancellationException

import cats.effect.{Async, Resource}
import java.util.concurrent.CompletionStage

trait CatsEffect {
  implicit final def catsAsync[F[_]](implicit F: Async[F]): neotypes.Async.Aux[F, FResource[F]#R] =
    new neotypes.Async[F] {
      override final type R[A] = Resource[F, A]

      override final def fromCompletionStage[A](completionStage: => CompletionStage[A]): F[A] =
        F.fromCompletionStage(F.delay(completionStage))

      override final def delay[A](a: => A): F[A] =
        F.delay(a)

      override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] =
        F.flatMap(fa)(f)

      override final def fromEither[A](e: => Either[Throwable, A]): F[A] =
        F.defer(F.fromEither(e))

      override final def guarantee[A, B](fa: F[A])
                                        (f: A => F[B])
                                        (finalizer: (A, Option[Throwable]) => F[Unit]): F[B] =
        Resource.makeCase(fa) {
          case (a, Resource.ExitCase.Succeeded) =>
            finalizer(a, None)

          case (a, Resource.ExitCase.Canceled) =>
            finalizer(a, Some(CancellationException))

          case (a, Resource.ExitCase.Errored(ex)) =>
            finalizer(a, Some(ex))
        }.use(f)

      override final def map[A, B](fa: F[A])(f: A => B): F[B] =
        F.map(fa)(f)

      override final def mapError[A](fa: F[A])(f: Throwable => Throwable): F[A] =
        F.adaptError(fa)(PartialFunction.fromFunction(f))

      override final def resource[A](input: => A)(close: A => F[Unit]): Resource[F, A] =
        Resource.make(F.delay(input))(close)
    }
}
