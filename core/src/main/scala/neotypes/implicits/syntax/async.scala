package neotypes
package implicits.syntax

import scala.language.{higherKinds, implicitConversions}

private[neotypes] trait AsyncSyntax {
  implicit final def neotypesSyntaxAsync[F[_], A](fa: F[A]): AsyncOps[F, A] =
    new AsyncOps(fa)
}

final class AsyncOps[F[_], A](private val fa: F[A]) extends AnyVal {
  def map[B](f: A => B)(implicit F: Async[F]): F[B] =
    F.map(fa)(f)

  def flatMap[B](f: A => F[B])(implicit F: Async[F]): F[B] =
    F.flatMap(fa)(f)

  def recoverWith[B >: A](f: PartialFunction[Throwable, F[B]])(implicit F: Async[F]): F[B] =
    F.recoverWith[A, B](fa)(f)
}
