package neotypes
package internal.syntax

import scala.collection.Factory

private[neotypes] object stream {
  implicit class StreamOps[S[_], A](private val sa: S[A]) extends AnyVal {
    def andThen[B](sb: S[B])(implicit S: Stream[S]): S[Either[A, B]] =
      S.append(S.map(sa)(Left.apply), S.map(sb)(Right.apply))

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

    def voidS[F[_]](implicit S: Stream.Aux[S, F]): F[Unit] =
      S.void(sa)
  }
}
