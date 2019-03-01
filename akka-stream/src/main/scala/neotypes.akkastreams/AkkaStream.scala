package neotypes.akkastreams

import akka.NotUsed
import akka.stream.scaladsl.Source
import neotypes.Stream
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AkkaStream(implicit ec: ExecutionContext) extends neotypes.StreamBuilder[AkkaStream.Stream, Future] {

  def init[T](): Stream[T, AkkaStream.Stream, Future] = new Stream[T, AkkaStream.Stream, Future] {

    var nextF: Unit => Future[Option[T]] = _

    override def next(value: () => Future[Option[T]]): Unit = nextF = _ => value()

    override def failure(ex: Throwable): Unit = nextF = _ => Future.failed(ex)

    override def toStream: AkkaStream.Stream[T] =
      Source
        .repeat()
        .mapAsync[Option[T]](1)(_ => nextF())
        .takeWhile(_.isDefined)
        .map(_.get)
  }
}

object AkkaStream {
  type Stream[T] = Source[T, NotUsed]

  implicit def akkaStream(implicit ec: ExecutionContext): neotypes.StreamBuilder[AkkaStream.Stream, Future] = new AkkaStream
}
