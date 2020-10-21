package neotypes.akkastreams

import akka.stream.{Attributes, Materializer, SourceShape, Outlet}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.compat.Factory

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
  implicit final def akkaStream(implicit mat: Materializer): neotypes.Stream.Aux[AkkaStream, Future] =
    new neotypes.Stream[AkkaStream] {
      import mat.executionContext

      /** Define effect type to be the scala Future
        *
        * @tparam T parametric type for scala Future
        */
      override final type F[T] = Future[T]

      // Legacy module ------------------------------------------------------------
      /** Initialize AkkaStream
        *
        * @param value stream function to be applied
        * @tparam T parametric type
        * @return neotype AkkaStream
        */
      override final def init[T](value: () => Future[Option[T]]): AkkaStream[T] =
        Source
          .repeat(())
          .mapAsync(parallelism = 1)(_ => value())
          .takeWhile(_.isDefined)
          .map(_.get)

      /** Apply side effect to AkkaStream
        *
        * @param s neotypes AkkaStream
        * @param f lazily evaluated scala Future side effect
        * @tparam T parametric type
        * @return neotypes AkkaStream
        */
      override final def onComplete[T](s: AkkaStream[T])(f: => Future[Unit]): AkkaStream[T] =
        s.watchTermination() { (mat, done) =>
          done.flatMap(_ => f)
          mat
        }

      /** Evaluate Future AkkaStream
        *
        * @param f Future AkkaStream
        * @tparam T parametric type
        * @return the evaluated AkkaStream
        */
      override final def fToS[T](f: Future[AkkaStream[T]]): AkkaStream[T] =
        Source.futureSource(f).viaMat(Flow[T])(Keep.none)
      // --------------------------------------------------------------------------


      // New (Rx) module ----------------------------------------------------------
      override final def fromRx[A](publisher: Publisher[A]): AkkaStream[A] =
        Source.fromPublisher(publisher)

      override final def fromF[A](fa: Future[A]): AkkaStream[A] =
        Source.future(fa)

      override final def resource[A](r: Future[A])(finalizer: (A, Option[Throwable]) => Future[Unit]): AkkaStream[A] =
        Source.fromGraph(new AkkaStreams.ResourceStage[A](r, finalizer))

      override final def map[A, B](sa: AkkaStream[A])(f: A => B): AkkaStream[B] =
        sa.map(f)

      override final def flatMap[A, B](sa: AkkaStream[A])(f: A => AkkaStream[B]): AkkaStream[B] =
        sa.flatMapConcat(f)

      override final def evalMap[A, B](sa: AkkaStream[A])(f: A => F[B]): AkkaStream[B] =
        sa.mapAsync(parallelism = 1)(f)

      override final def collectAs[C, A](sa: AkkaStream[A])(factory: Factory[A, C]): Future[C] = {
        // Thanks to Jasper Moeys (@Jasper-M) for providing this workaround.
        // We are still not sure this is totally safe, if you find a bug please let's us know.
        type CC[x] = C
        val f: Factory[A, CC[A]] = factory
        sa.runWith(Sink.seq).map(seq => seq.to(f))
      }

      override final def single[A](sa: AkkaStream[A]): Future[A] =
        sa.take(1).runWith(Sink.last)

      override final def void(s: AkkaStream[_]): Future[Unit] =
        s.runWith(Sink.ignore).map(_ => ())
      // --------------------------------------------------------------------------
    }
}

object AkkaStreams {
  private final class ResourceStage[A] private[AkkaStreams] (factory: Future[A], finalizer: (A, Option[Throwable]) => Future[Unit])
                                                            (implicit ec: ExecutionContext) extends GraphStage[SourceShape[A]] {
    val out: Outlet[A] = Outlet("NeotypesResourceSource")
    override val shape: SourceShape[A] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var alreadyCreated = false
      private var resource: A = _

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (!alreadyCreated) {
            val callback = getAsyncCallback[A] { a =>
              alreadyCreated = true
              resource = a
              push(out, a)
            }
            factory.foreach(callback.invoke)
          } else {
            val callback = getAsyncCallback[Unit](_ => complete(out))
            finalizer(resource, None).foreach(callback.invoke)
          }
        }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          val callback = getAsyncCallback[Unit](_ => complete(out))
          finalizer(resource, Option(cause)).foreach(callback.invoke)
        }
      })
    }
  }
}
