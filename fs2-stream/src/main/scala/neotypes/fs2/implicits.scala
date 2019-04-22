package neotypes.fs2

object implicits {
  implicit def fs2Stream[_F[_]](implicit F: cats.effect.Sync[_F]): neotypes.Stream.Aux[Fs2FStream[_F]#T, _F] =
    new neotypes.Stream[Fs2FStream[_F]#T] {
      override type F[T] = _F[T]

      override def init[T](value: () => F[Option[T]]): fs2.Stream[F, T] =
        fs2.Stream.repeatEval(F.suspend(value())).unNoneTerminate

      override def onComplete[T](s: fs2.Stream[F, T])(f: => F[Unit]): fs2.Stream[F, T] =
        s ++ fs2.Stream.eval_(f)

      override def fToS[T](f: F[fs2.Stream[F, T]]): fs2.Stream[F, T] =
        fs2.Stream.eval(f).flatten
    }
}
