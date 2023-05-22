package neotypes.zio

import zio.stream.ZStream

package object stream {
  type ZioStream[A] = ZStream[Any, Throwable, A]

  final object implicits extends ZioStreams
}
