package neotypes

@annotation.implicitNotFound("The stream type ${S} is not supported by neotypes")
trait Stream[S[_]] {
  type F[T]
}

object Stream {
  type Aux[S[_], _F[_]] = Stream[S] { type F[T] = _F[T] }
}
