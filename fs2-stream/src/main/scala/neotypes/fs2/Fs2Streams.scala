package neotypes.fs2

import language.higherKinds

trait Fs2Streams {
  implicit final def fs2Stream[_F[_]](implicit F: cats.effect.Async[_F]): neotypes.Stream.Aux[Fs2FStream[_F]#T, _F] =
    new neotypes.Stream[Fs2FStream[_F]#T] {
      override final type F[T] = _F[T]

      override final def init[T](value: () => F[Option[T]]): fs2.Stream[F, T] =
        fs2.Stream.repeatEval(F.suspend(value())).unNoneTerminate

      override final def onComplete[T](s: fs2.Stream[F, T])(f: => F[Unit]): fs2.Stream[F, T] =
        s.onFinalize(f)

      override final def fToS[T](f: F[fs2.Stream[F, T]]): fs2.Stream[F, T] =
        fs2.Stream.eval(f).flatten
    }
}
