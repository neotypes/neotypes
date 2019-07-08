package neotypes.cats.effect

import scala.language.higherKinds
import cats.effect.{Async => CatsAsync, Resource}

trait CatsEffect {
  private[neotypes] final type FResource[F[_]] = { type R[A] = Resource[F, A] }

  implicit final def catsAsync[F[_]](implicit F: CatsAsync[F]): neotypes.Async.Aux[F, FResource[F]#R] =
    new neotypes.Async[F] {
      override final type R[A] = Resource[F, A]

      override final def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T] =
        F.async(cb)

      override final def delay[A](t: => A): F[A] =
        F.delay(t)

      override final def failed[T](e: Throwable): F[T] =
        F.raiseError(e)

      override final def flatMap[T, U](m: F[T])(f: T => F[U]): F[U] =
        F.flatMap(m)(f)

      override final def map[T, U](m: F[T])(f: T => U): F[U] =
        F.map(m)(f)

      override final def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U] =
        F.recoverWith(F.map(m)(identity[U]))(f)

      override final def resource[A](input: F[A])(close: A => F[Unit]): Resource[F, A] =
        Resource.make(input)(close)
    }
}
