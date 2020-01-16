package neotypes.akkastreams

import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContext, Future}

/**
  * neotypes Akka Streams
  * @see <a href = https://neotypes.github.io/neotypes/docs/streams.html#akka-streams-neotypes-akka-stream>AkkaStreams example</a>
  */
trait AkkaStreams {

  /** neotypes support for Akka Streams
    *
    * @param ec implicit scala.concurrent ExecutionContext that will be passed down to all Akka Streams operations
    * @return neotype AkkaStream with effect type as the scala.concurrent Future
    */
  implicit final def akkaStream(implicit ec: ExecutionContext): neotypes.Stream.Aux[AkkaStream, Future] =
    new neotypes.Stream[AkkaStream] {
      /** Define effect type to be the scala Future
        *
        * @tparam T parametric type for scala Future
        */
      override final type F[T] = Future[T]

      /** Initialize AkkaStream
        *
        * @param value stream function to be applied
        * @tparam T parametric type
        * @return neotype AkkaStream
        */
      override final def init[T](value: () => Future[Option[T]]): AkkaStream[T] =
        Source
          .repeat(())
          .mapAsync(1){ _ => value() }
          .takeWhile(_.isDefined)
          .map(_.get)
          .viaMat(Flow[T]) { (_, _) => Future.successful(()) }

      /** Apply side effect to AkkaStream
        *
        * @param s neotypes AkkaStream
        * @param f lazily evaluated scala Future side effect
        * @tparam T parametric type
        * @return neotypes AkkaStream
        */
      override final def onComplete[T](s: AkkaStream[T])(f: => Future[Unit]): AkkaStream[T] =
        s.watchTermination() { (_, done) =>
          done.flatMap(_ => f)
        }

      /** Evaluate Future AkkaStream
        *
        * @param f Future AkkaStream
        * @tparam T parametric type
        * @return the evaluated AkkaStream
        */
      override final def fToS[T](f: Future[AkkaStream[T]]): AkkaStream[T] =
        Source
          .futureSource(f)
          .viaMat(Flow[T]) { (m, _) => m.flatMap(identity) }
    }
}
