package neotypes.akkastreams

import akka.stream.{Attributes, Materializer, SourceShape, Outlet}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.compat._

/**
  * neotypes Akka Streams
  * @see <a href = https://neotypes.github.io/neotypes/docs/streams.html#akka-streams-neotypes-akka-stream>AkkaStreams example</a>
  */
trait AkkaStreams {

  /** neotypes support for Akka Streams
    *
    * @param mat implicit [[Materializer]] that will be passed down to all Akka Streams operations.
    *            This also provides the [[ExecutionContext]] that is used to run the futures produced by the stream.
    * @return neotype AkkaStream with effect type as the scala.concurrent Future
    */
  implicit final def akkaStream(implicit mat: Materializer): neotypes.Stream.Aux[AkkaStream, Future] =
    new neotypes.Stream[AkkaStream] {
      import mat.executionContext

      override final type F[T] = Future[T]

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

      override final def collectAs[C, A](sa: AkkaStream[A])(factory: Factory[A, C]): Future[C] =
        sa.runWith(Sink.fold(factory.newBuilder)(_ += _)).map(_.result())

      override final def single[A](sa: AkkaStream[A]): Future[A] =
        sa.take(1).runWith(Sink.last)

      override final def void(s: AkkaStream[_]): Future[Unit] =
        s.runWith(Sink.ignore).map(_ => ())
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
