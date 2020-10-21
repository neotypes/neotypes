package neotypes

import akka.NotUsed
import akka.stream.scaladsl.Source

package object akkastreams {
  type AkkaStream[T] = Source[T, NotUsed]

  final object implicits extends AkkaStreams
}
