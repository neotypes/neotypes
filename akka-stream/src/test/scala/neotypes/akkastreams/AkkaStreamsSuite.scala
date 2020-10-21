package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import neotypes.{FutureTestkit, Stream, StreamSuite, StreamTestkit}
import neotypes.akkastreams.implicits._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.concurrent.duration.Duration

/** Implementation of the Stream Teskit for akka streams. */
object AkkaStreamsTestkit extends StreamTestkit[AkkaStream, Future](FutureTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val system =
        ActorSystem("QuickStart")

      override def streamToFList[T](stream: AkkaStream[T]): Future[List[T]] =
        stream.runWith(Sink.seq).map(_.toList)

      override final val streamInstance: Stream.Aux[AkkaStream, Future] =
        implicitly
    }
}

/** Execute all the stream specs using akka streams. */
final class AkkaStreamsSuite extends StreamSuite(AkkaStreamsTestkit)
