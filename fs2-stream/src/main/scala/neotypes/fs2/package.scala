package neotypes

package object fs2 {
  type Fs2FStream[F[_]] = { type T[A] = _root_.fs2.Stream[F, A] }

  type Fs2IoStream[T] = _root_.fs2.Stream[cats.effect.IO, T]
}
