package neotypes.cats

import cats.{ApplicativeError, effect}
import neotypes.Async

class CatsAsync[F[_]](implicit e: effect.Async[F], ae: ApplicativeError[F, Throwable]) extends Async[F] {

  override def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T] = e.async(cb)

  override def flatMap[T, U](m: F[T])(f: T => F[U]): F[U] = e.flatMap(m)(f)

  override def map[T, U](m: F[T])(f: T => U): F[U] = e.map(m)(f)

  override def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U] =
    e.recoverWith(m)(f.asInstanceOf[PartialFunction[Throwable, F[T]]]).asInstanceOf[F[U]]

  override def failed[T](e: Throwable): F[T] = ae.raiseError(e)
}

object implicits {
  implicit def catsAsync[F[_]](implicit e: effect.Async[F]): Async[F] = new CatsAsync
}