package neotypes.zio

package object stream {
  type ZioStream[T] = _root_.zio.stream.ZStream[Any, Throwable, T]

  final object implicits extends ZioStreams
}
