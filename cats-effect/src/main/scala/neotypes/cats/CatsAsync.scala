package neotypes.cats

import cats.{ApplicativeError, effect}
import neotypes.Async

class CatsAsync[F[_]](implicit F: effect.Async[F]) extends Async[F] {

  override def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T] =
    F.async(cb)

  override def flatMap[T, U](m: F[T])(f: T => F[U]): F[U] =
    F.flatMap(m)(f)

  override def map[T, U](m: F[T])(f: T => U): F[U] =
    F.map(m)(f)

  override def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U] =
    F.recoverWith(F.map(m)(identity): F[U])(f)

  override def failed[T](e: Throwable): F[T] =
    F.raiseError(e)

  override def success[T](t: => T): F[T] =
    F.delay(t)
}

object implicits {
  implicit def catsAsync[F[_]](implicit e: effect.Async[F]): Async[F] = new CatsAsync
}
