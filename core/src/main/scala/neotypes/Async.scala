package neotypes

import scala.language.higherKinds

trait Async[F[_]] {
  def async[T](cb: (Either[Throwable, T] => Unit) => Unit): F[T]

  def flatMap[T, U](m: F[T])(f: T => F[U]): F[U]

  def map[T, U](m: F[T])(f: T => U): F[U]

  def recoverWith[T, U >: T](m: F[T])(f: PartialFunction[Throwable, F[U]]): F[U]

  def failed[T](e: Throwable): F[T]

  def success[T](t: => T): F[T]
}
