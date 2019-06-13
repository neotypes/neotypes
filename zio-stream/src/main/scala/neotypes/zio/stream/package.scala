package neotypes.zio

package object stream {
  type ZioStream[T] = _root_.zio.stream.ZStream[Any, Throwable, T]
}
