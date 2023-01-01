package neotypes.zio

package object stream {
  type ZioStream[A] = _root_.zio.stream.ZStream[Any, Throwable, A]

  object implicits extends ZioStreams
}
