package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import neotypes.{Async, StreamIntegrationSpec}
import neotypes.akkastreams.implicits._
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class AkkaStreamSpec extends StreamIntegrationSpec[AkkaStream, Future] {
  implicit val system =
    ActorSystem("QuickStart")

  override def fToFuture[T](future: Future[T]): Future[T] =
    future

  override def streamToFList[T](stream: AkkaStream[T]): Future[List[T]] =
    stream.runWith(Sink.seq[T]).map(_.toList).flatMap { result =>
      Future {
        // Delaying the answer a little seems to fix:
        // https://github.com/neotypes/neotypes/issues/47
        Thread.sleep(1000)
        result
      }
    }

  override def F: Async[Future] =
    implicitly

  override def S: neotypes.Stream.Aux[AkkaStream, Future] =
    implicitly
}
