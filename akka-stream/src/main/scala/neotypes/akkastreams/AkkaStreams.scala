package neotypes.akkastreams

import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContext, Future}

trait AkkaStreams {
  implicit final def akkaStream(implicit ec: ExecutionContext): neotypes.Stream.Aux[AkkaStream, Future] =
    new neotypes.Stream[AkkaStream] {
      override type F[T] = Future[T]

      override def init[T](value: () => Future[Option[T]]): AkkaStream[T] =
        Source
          .repeat(())
          .mapAsync(1){ _ => value() }
          .takeWhile(_.isDefined)
          .map(_.get)
          .viaMat(Flow[T]) { (_, _) => Future.successful(()) }

      override def onComplete[T](s: AkkaStream[T])(f: => Future[Unit]): AkkaStream[T] =
        s.watchTermination() { (_, done) =>
          done.flatMap(_ => f)
        }

      override def fToS[T](f: Future[AkkaStream[T]]): AkkaStream[T] =
        Source
          .fromFutureSource(f)
          .viaMat(Flow[T]) { (m, _) => m.flatMap(identity) }
    }
}
