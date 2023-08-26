package neotypes.zio

import zio.stream.ZStream

package object stream {
  type ZioStream[A] = ZStream[Any, Throwable, A]

  object implicits extends ZioStreams
}
