package neotypes

import _root_.cats.effect.IO
import _root_.fs2.{Stream => Fs2Stream}

package object fs2 {
  type Fs2FStream[F[_]] = { type T[A] = Fs2Stream[F, A] }

  type Fs2IoStream[A] = Fs2Stream[IO, A]

  object implicits extends Fs2Streams {
    implicit final val Fs2IoStream: neotypes.Stream.Aux[Fs2IoStream, IO] = fs2Stream
  }
}
