package neotypes.pekkostreams

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{Attributes, Materializer, Outlet, OverflowStrategy, SourceShape}
import org.apache.pekko.stream.scaladsl.{JavaFlowSupport, Sink, Source}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

import java.util.concurrent.Flow.Publisher
import scala.collection.Factory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** neotypes Pekko Streams
  * @see
  *   <a href = https://neotypes.github.io/neotypes/docs/streams.html#pekko-streams-neotypes-pekko-stream>PekkoStreams
  *   example</a>
  */
trait PekkoStreams {
  import PekkoStreams.GuaranteeStage

  /** neotypes support for Pekko Streams
    *
    * @param mat
    *   implicit org.apache.pekko.stream.Materializer that will be passed down to all Pekko Streams operations
    * @return
    *   [[neotypes.Stream]] for [[PekkoStream]] using [[scala.concurrent.Future]].
    */
  implicit final def pekkoStream(implicit mat: Materializer): neotypes.Stream.Aux[PekkoStream, Future] =
    new neotypes.Stream[PekkoStream] {
      implicit val ec: ExecutionContext = mat.executionContext

      override final type F[A] = Future[A]

      override final def fromPublisher[A](publisher: => Publisher[A], chunkSize: Int): PekkoStream[A] =
        JavaFlowSupport.Source.fromPublisher(publisher).buffer(chunkSize, OverflowStrategy.backpressure)

      override final def fromF[A](future: Future[A]): PekkoStream[A] =
        Source.future(future)

      override final def append[A, B >: A](sa: PekkoStream[A], sb: => PekkoStream[B]): PekkoStream[B] =
        sa.concatLazy(Source.lazySource(() => sb))

      override final def guarantee[A, B](
        r: Future[A]
      )(
        f: A => PekkoStream[B]
      )(
        finalizer: (A, Option[Throwable]) => Future[Unit]
      ): PekkoStream[B] =
        Source
          .lazySource(() => Source.fromGraph(new GuaranteeStage(r, finalizer)).flatMapConcat(f))
          .mapMaterializedValue(_ => NotUsed)

      override final def map[A, B](sa: PekkoStream[A])(f: A => B): PekkoStream[B] =
        sa.map(f)

      override final def flatMap[A, B](sa: PekkoStream[A])(f: A => PekkoStream[B]): PekkoStream[B] =
        sa.flatMapConcat(f)

      override final def evalMap[A, B](sa: PekkoStream[A])(f: A => Future[B]): PekkoStream[B] =
        sa.mapAsync(parallelism = 1)(f)

      override final def collect[A, B](sa: PekkoStream[A])(pf: PartialFunction[A, B]): PekkoStream[B] =
        sa.collect(pf)

      override final def collectAs[A, C](sa: PekkoStream[A])(factory: Factory[A, C]): Future[C] =
        sa.runWith(Sink.collection(factory.asInstanceOf[Factory[A, C with collection.immutable.Iterable[A]]]))

      override final def single[A](sa: PekkoStream[A]): Future[Option[A]] =
        sa.take(1).runWith(Sink.lastOption)

      override final def void[A](s: PekkoStream[A]): Future[Unit] =
        s.runWith(Sink.ignore).map(_ => ())
    }
}

object PekkoStreams {
  private final class GuaranteeStage[A](
    factory: Future[A],
    finalizer: (A, Option[Throwable]) => Future[Unit]
  )(implicit
    ec: ExecutionContext
  ) extends GraphStage[SourceShape[A]] {
    val out: Outlet[A] = Outlet("NeotypesGuaranteeSource")
    override val shape: SourceShape[A] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var alreadyCreated = false
      private var resource: A = _

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            val failureCallback = getAsyncCallback[Throwable](t => fail(out, t))

            if (!alreadyCreated) {
              val successCallback = getAsyncCallback[A] { a =>
                alreadyCreated = true
                resource = a
                push(out, a)
              }

              factory.onComplete {
                case Success(v) => successCallback.invoke(v)
                case Failure(e) => failureCallback.invoke(e)
              }
            } else {
              val successCallback = getAsyncCallback[Unit](_ => complete(out))

              finalizer(resource, None).onComplete {
                case Success(v) => successCallback.invoke(v)
                case Failure(e) => failureCallback.invoke(e)
              }
            }
          }

          override def onDownstreamFinish(cause: Throwable): Unit = {
            val callback = getAsyncCallback[Unit](_ => complete(out))
            finalizer(resource, Option(cause)).foreach(callback.invoke)
          }
        }
      )
    }
  }
}
