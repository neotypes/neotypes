package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import neotypes.{FutureTestkit, Stream, StreamSuite, StreamTestkit}
import neotypes.akkastreams.implicits._
import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Stream Testkit for Akka streams. */
object AkkaStreamsTestkit extends StreamTestkit[Future, AkkaStream](FutureTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val system =
        ActorSystem(name = "QuickStart")

      override def streamToFList[T](stream: AkkaStream[T]): Future[List[T]] =
        stream.runWith(Sink.seq).map(_.toList)

      override final val streamInstance: Stream.Aux[AkkaStream, Future] =
        implicitly
    }
}

/** Execute all the stream specs using Akka streams. */
final class AkkaStreamsSuite extends StreamSuite(AkkaStreamsTestkit)
