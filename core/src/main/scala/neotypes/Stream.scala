package neotypes

trait Stream[S[_], F[_]] {
  def init[T](value: () => F[Option[T]]): S[T]

  def onComplete[T](s: S[T])(f: => F[Unit]): S[T]

  def fToS[T](f: F[S[T]]): S[T]
}