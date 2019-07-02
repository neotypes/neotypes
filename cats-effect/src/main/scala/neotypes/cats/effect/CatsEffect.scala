package neotypes.cats.effect

import scala.language.higherKinds

trait CatsEffect {
  implicit final def catsAsync[F[_]](implicit F: cats.effect.Async[F]): neotypes.Async[F] =
    new neotypes.Async[F] {
      override def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T] =
        F.async(cb)

      override def delay[A](t: => A): F[A] =
        F.delay(t)

      override def flatMap[T, U](m: F[T])(f: T => F[U]): F[U] =
        F.flatMap(m)(f)

      override def map[T, U](m: F[T])(f: T => U): F[U] =
        F.map(m)(f)

      override def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U] =
        F.recoverWith(F.map(m)(identity[U]))(f)

      override def failed[T](e: Throwable): F[T] =
        F.raiseError(e)

      override def suspend[A](t: => F[A]): F[A] =
        F.suspend(t)
    }
}
