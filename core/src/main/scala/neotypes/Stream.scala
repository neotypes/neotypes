package neotypes

trait StreamBuilder[S[_], F[_]] {
  def init[T](): Stream[T, S, F]

  def onComplete[T](s: S[T])(f: => F[Unit]): S[T]

  def fToS[T](f: F[S[T]]): S[T]
}

trait Stream[T, S[_], F[_]] {

  def next(value: () => F[Option[T]]): Unit

  def toStream: S[T]
}