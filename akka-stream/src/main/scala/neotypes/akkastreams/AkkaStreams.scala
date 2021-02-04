package neotypes.akkastreams

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.reactivestreams.Publisher

import scala.concurrent.Future
import scala.collection.compat._
import scala.util.{Failure, Success}

/**
  * neotypes Akka Streams
  * @see <a href = https://neotypes.github.io/neotypes/docs/streams.html#akka-streams-neotypes-akka-stream>AkkaStreams example</a>
  */
trait AkkaStreams {
  /** neotypes support for Akka Streams
    *
    * @param mat implicit akka.stream.Materializer that will be passed down to all Akka Streams operations
    * @return neotype AkkaStream with effect type as the scala.concurrent Future
    */
  implicit final def akkaStream(implicit mat: Materializer): neotypes.Stream.Aux[AkkaStream, Future] =
    new neotypes.Stream[AkkaStream] {
      implicit val ec = mat.executionContext

      /** Define effect type to be the scala Future
        *
        * @tparam T parametric type for scala Future
        */
      override final type F[A] = Future[A]

      override final def fromRx[A](publisher: Publisher[A]): AkkaStream[A] =
        Source.fromPublisher(publisher)

      override def fromF[A](future: Future[A]): AkkaStream[A] =
        Source.future(future)

      override final def resource[A, B](r: Future[A])(f: A => AkkaStream[B])(finalizer: (A, Option[Throwable]) => Future[Unit]): AkkaStream[B] =
        Source.future(r).flatMapConcat { a =>
          f(a).watchTermination() {
            case (mat, f) =>
              f.onComplete {
                case Success(_)  => finalizer(a, None)
                case Failure(ex) => finalizer(a, Some(ex))
              }
              mat
          }
        }

      override final def map[A, B](sa: AkkaStream[A])(f: A => B): AkkaStream[B] =
        sa.map(f)

      override final def flatMap[A, B](sa: AkkaStream[A])(f: A => AkkaStream[B]): AkkaStream[B] =
        sa.flatMapConcat(f)

      override final def evalMap[A, B](sa: AkkaStream[A])(f: A => Future[B]): AkkaStream[B] =
        sa.mapAsync(parallelism = 1)(f)

      override final def collectAs[A, C](sa: AkkaStream[A])(factory: Factory[A,C]): Future[C] =
        sa.runWith(Sink.fold(factory.newBuilder)(_ += _)).map(_.result())

      override final def single[A](sa: AkkaStream[A]): Future[Option[A]] =
        sa.take(1).runWith(Sink.lastOption)

      override final def void(s: AkkaStream[_]): Future[Unit] =
        s.runWith(Sink.ignore).map(_ => ())
    }
}
