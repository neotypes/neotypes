package neotypes

trait Stream[S[_]] {
  type F[T]

  def init[T](value: () => F[Option[T]]): S[T]

  def onComplete[T](s: S[T])(f: => F[Unit]): S[T]

  def fToS[T](f: F[S[T]]): S[T]
}

object Stream {
  type Aux[S[_], _F[_]] = Stream[S] { type F[T] = _F[T] }
}
