package neotypes.akkastreams

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.akkastreams.implicits._
import neotypes.future.FutureTestkit

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Merge, Source}

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Stream Testkit for Akka streams. */
object AkkaStreamsTestkit extends StreamTestkit[AkkaStream, Future, {}](FutureTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit final val system: ActorSystem =
        ActorSystem(name = "QuickStart")

      override final val streamInstance: Stream.Aux[AkkaStream, Future] =
        implicitly

      override def streamConcurrently(stream1: AkkaStream[Unit], stream2: AkkaStream[Unit]): AkkaStream[Unit] =
        Source.combine(stream1, stream2)(Merge(_))
    }
}

/** Execute all the Stream specs using Akka streams. */
final class AkkaStreamsSuite extends StreamSuite(AkkaStreamsTestkit)
