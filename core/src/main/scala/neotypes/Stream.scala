package neotypes

import org.reactivestreams.Publisher
import scala.collection.compat.Factory

@annotation.implicitNotFound("The stream type ${S} is not supported by neotypes")
trait Stream[S[_]] {
  type F[T]

  private[neotypes] def fromRx[A](publisher: Publisher[A]): S[A]

  private[neotypes] def fromF[A](fa: F[A]): S[A]

  private[neotypes] def resource[A, B](r: F[A])(f: A => S[B])(finalizer: (A, Option[Throwable]) => F[Unit]): S[B]

  private[neotypes] def map[A, B](sa: S[A])(f: A => B): S[B]

  private[neotypes] def flatMap[A, B](sa: S[A])(f: A => S[B]): S[B]

  private[neotypes] def evalMap[A, B](sa: S[A])(f: A => F[B]): S[B]

  private[neotypes] def collectAs[A, C](sa: S[A])(factory: Factory[A, C]): F[C]

  private[neotypes] def single[A](sa: S[A]): F[Option[A]]

  private[neotypes] def void(s: S[_]): F[Unit]
}

object Stream {
  type Aux[S[_], _F[_]] = Stream[S] { type F[T] = _F[T] }
}
