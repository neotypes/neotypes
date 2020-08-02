package neotypes

import org.reactivestreams.Publisher

import scala.collection.compat.Factory

@annotation.implicitNotFound("The stream type ${S} is not supported by neotypes")
trait Stream[S[_]] {
  type F[T]

  // Legacy module ------------------------------------------------------------
  private[neotypes] def init[A](value: () => F[Option[A]]): S[A]

  private[neotypes] def onComplete[A](s: S[A])(f: => F[Unit]): S[A]

  private[neotypes] def fToS[A](f: F[S[A]]): S[A]
  // --------------------------------------------------------------------------

  // New (Rx) module ----------------------------------------------------------
  private[neotypes] def fromRx[A](publisher: Publisher[A]): S[A]

  private[neotypes] def fromF[A](fa: F[A]): S[A]

  private[neotypes] def resource[A](r: F[A])(finalizer: (A, Option[Throwable]) => F[Unit]): S[A]

  private[neotypes] def map[A, B](sa: S[A])(f: A => B): S[B]

  private[neotypes] def flatMap[A, B](sa: S[A])(f: A => S[B]): S[B]

  private[neotypes] def evalMap[A, B](sa: S[A])(f: A => F[B]): S[B]

  private[neotypes] def collectAs[C, A](sa: S[A])(factory: Factory[A, C]): F[C]

  private[neotypes] def single[A](sa: S[A]): F[A]

  private[neotypes] def void(s: S[_]): F[Unit]
  // --------------------------------------------------------------------------
}

object Stream {
  type Aux[S[_], _F[_]] = Stream[S] { type F[T] = _F[T] }
}
