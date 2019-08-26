package neotypes

import akka.stream.scaladsl.Source

import scala.concurrent.Future

package object akkastreams {
  type AkkaStream[T] = Source[T, Future[Unit]]

  final object implicits extends AkkaStreams
}
