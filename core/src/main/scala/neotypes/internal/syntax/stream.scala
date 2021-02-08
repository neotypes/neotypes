package neotypes
package internal.syntax

import org.reactivestreams.Publisher

import scala.collection.compat.Factory

private[neotypes] object stream {
  implicit class StreamOps[S[_], A](private val sa: S[A]) extends AnyVal {
    def mapS[B](f: A => B)(implicit S: Stream[S]): S[B] =
      S.map(sa)(f)

    def flatMapS[B](f: A => S[B])(implicit S: Stream[S]): S[B] =
      S.flatMap(sa)(f)

    def evalMap[F[_], B](f: A => F[B])(implicit S: Stream.Aux[S, F]): S[B] =
      S.evalMap(sa)(f)

    def collectAs[F[_], C](factory: Factory[A, C])(implicit S: Stream.Aux[S, F]): F[C] =
      S.collectAs(sa)(factory)

    def single[F[_]](implicit S: Stream.Aux[S, F]): F[Option[A]] =
      S.single(sa)

    def void[F[_]](implicit S: Stream.Aux[S, F]): F[Unit] =
      S.void(sa)
  }

  implicit class PublisherOps[A](private val publisher: Publisher[A]) extends AnyVal {
    def toStream[S[_]](implicit S: Stream[S]): S[A] =
      S.fromRx(publisher)
  }
}
