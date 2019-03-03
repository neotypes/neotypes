package neotypes.akkastreams

import akka.NotUsed
import akka.stream.impl.fusing.GraphStages
import akka.stream.scaladsl.{Flow, Sink, Source}
import neotypes.Stream

import scala.concurrent.{ExecutionContext, Future}

class AkkaStream(implicit ec: ExecutionContext) extends neotypes.StreamBuilder[AkkaStream.Stream, Future] {

  def init[T](): Stream[T, AkkaStream.Stream, Future] = new Stream[T, AkkaStream.Stream, Future] {

    var nextF: Unit => Future[Option[T]] = _

    override def next(value: () => Future[Option[T]]): Unit = nextF = _ => value()

    override def toStream: AkkaStream.Stream[T] =
      Source
        .repeat()
        .mapAsync[Option[T]](1)(_ => nextF()) // TODO fix npe
        .takeWhile(_.isDefined)
        .map(_.get)
        .viaMat(Flow[T])((_, _) => Future.successful(()))
  }

  override def onComplete[T](s: AkkaStream.Stream[T])(f: => Future[Unit]): AkkaStream.Stream[T] =
    s.watchTermination() {
      (_, done) =>
        done.flatMap(_ => f)
    }

  override def fToS[T](f: Future[AkkaStream.Stream[T]]): AkkaStream.Stream[T] = {
    Source.fromFutureSource(f).viaMat(Flow[T])((m, _) => m.flatMap(identity))
  }
}

object AkkaStream {
  type Stream[T] = Source[T, Future[Unit]]

  implicit def akkaStream(implicit ec: ExecutionContext): neotypes.StreamBuilder[AkkaStream.Stream, Future] = new AkkaStream
}
