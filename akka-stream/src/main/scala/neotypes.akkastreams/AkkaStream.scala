package neotypes.akkastreams

import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContext, Future}

class AkkaStream(implicit ec: ExecutionContext) extends neotypes.Stream[AkkaStream.Stream, Future] {

  def init[T](value: () => Future[Option[T]]): AkkaStream.Stream[T] =
    Source
      .repeat()
      .mapAsync[Option[T]](1)(_ => value())
      .takeWhile(_.isDefined)
      .map(_.get)
      .viaMat(Flow[T])((_, _) => Future.successful(()))

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

  implicit def akkaStream(implicit ec: ExecutionContext): neotypes.Stream[AkkaStream.Stream, Future] = new AkkaStream
}
