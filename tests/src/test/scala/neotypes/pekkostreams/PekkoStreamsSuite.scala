package neotypes.pekkostreams

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.pekkostreams.implicits._
import neotypes.future.FutureTestkit

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Merge, Source}

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Stream Testkit for Pekko streams. */
object PekkoStreamsTestkit extends StreamTestkit[PekkoStream, Future](FutureTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit final val system: ActorSystem =
        ActorSystem(name = "QuickStart")

      override final val streamInstance: Stream.Aux[PekkoStream, Future] =
        implicitly

      override def streamConcurrently(stream1: PekkoStream[Unit], stream2: PekkoStream[Unit]): PekkoStream[Unit] =
        Source.combine(stream1, stream2)(Merge(_))
    }
}

/** Execute all the Stream specs using Pekko streams. */
final class PekkoStreamsSuite extends StreamSuite(PekkoStreamsTestkit)
