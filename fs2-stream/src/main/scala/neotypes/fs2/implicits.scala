package neotypes.fs2

object implicits {
  implicit def fs2Stream[F[_]](implicit F: cats.effect.Sync[F]): neotypes.Stream[Fs2FStream[F]#T, F] =
    new neotypes.Stream[Fs2FStream[F]#T, F] {
      override def init[T](value: () => F[Option[T]]): fs2.Stream[F, T] =
        fs2.Stream.repeatEval(F.suspend(value())).unNoneTerminate

      override def onComplete[T](s: fs2.Stream[F, T])(f: => F[Unit]): fs2.Stream[F, T] =
        s ++ fs2.Stream.eval_(f)

      override def fToS[T](f: F[fs2.Stream[F, T]]): fs2.Stream[F, T] =
        fs2.Stream.eval(f).flatten
    }
}
