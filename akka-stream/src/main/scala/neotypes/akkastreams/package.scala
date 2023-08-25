package neotypes

import akka.NotUsed
import akka.stream.scaladsl.Source

package object akkastreams {
  type AkkaStream[T] = Source[T, NotUsed]

  object implicits extends AkkaStreams
}
