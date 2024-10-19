package neotypes

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

package object pekkostreams {
  type PekkoStream[T] = Source[T, NotUsed]

  object implicits extends PekkoStreams
}
