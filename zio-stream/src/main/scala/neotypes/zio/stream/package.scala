package neotypes.zio

package object stream {
  type ZioStream[A] = _root_.zio.stream.ZStream[Any, Throwable, A]

  final object implicits extends ZioStreams
}
