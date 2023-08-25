package neotypes

import java.util.concurrent.Flow.Publisher
import scala.collection.Factory

@annotation.implicitNotFound("The Stream type ${S} is not supported by neotypes")
trait Stream[S[_]] {
  type F[T]

  private[neotypes] def fromPublisher[A](publisher: => Publisher[A], chunkSize: Int = 1): S[A]

  private[neotypes] def fromF[A](fa: F[A]): S[A]

  private[neotypes] def append[A, B >: A](sa: S[A], sb: => S[B]): S[B]

  private[neotypes] def guarantee[A, B](r: F[A])(f: A => S[B])(finalizer: (A, Option[Throwable]) => F[Unit]): S[B]

  private[neotypes] def map[A, B](sa: S[A])(f: A => B): S[B]

  private[neotypes] def flatMap[A, B](sa: S[A])(f: A => S[B]): S[B]

  private[neotypes] def evalMap[A, B](sa: S[A])(f: A => F[B]): S[B]

  private[neotypes] def collect[A, B](sa: S[A])(pf: PartialFunction[A, B]): S[B]

  private[neotypes] def collectAs[A, C](sa: S[A])(factory: Factory[A, C]): F[C]

  private[neotypes] def single[A](sa: S[A]): F[Option[A]]

  private[neotypes] def void[A](s: S[A]): F[Unit]
}

object Stream {
  type Aux[S[_], _F[_]] = Stream[S] { type F[T] = _F[T] }
}
