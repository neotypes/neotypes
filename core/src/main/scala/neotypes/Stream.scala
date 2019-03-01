package neotypes

trait StreamBuilder[S[_], F[_]] {
  def init[T](): Stream[T, S, F]
}

trait Stream[T, S[_], F[_]] {

  def next(value: () => F[Option[T]]): Unit

  def failure(ex: Throwable): Unit

  def toStream: S[T]
}