package neotypes.fs2

import cats.effect

private[neotypes] final class Fs2Stream[F[_]](implicit F: effect.Sync[F]) extends neotypes.Stream[({ type T[A] = fs2.Stream[F, A] })#T, F] {
  def init[T](value: () => F[Option[T]]): fs2.Stream[F, T] =
    fs2.Stream.repeatEval(F.suspend(value())).unNoneTerminate

  override def onComplete[T](s: fs2.Stream[F, T])(f: => F[Unit]): fs2.Stream[F, T] =
    s ++ fs2.Stream.eval_(f)

  override def fToS[T](f: F[fs2.Stream[F, T]]): fs2.Stream[F, T] =
    fs2.Stream.eval(f).flatten
}

object implicits {
  implicit def fs2Stream[F[_]](implicit F: effect.Sync[F]): neotypes.Stream[({ type T[A] = fs2.Stream[F, A] })#T, F] =
    new Fs2Stream
}
