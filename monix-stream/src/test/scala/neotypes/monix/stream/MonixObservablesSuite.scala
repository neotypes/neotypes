package neotypes.monix.stream

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.monix.MonixTaskTestkit
import neotypes.monix.stream.implicits._
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for Monix Observable. */
object MonixObservablesTestkit extends StreamTestkit[MonixStream, Task](MonixTaskTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override def streamToFList[A](stream: MonixStream[A]): Task[List[A]] =
        stream.toListL

      override final val streamInstance: Stream.Aux[MonixStream, Task] =
        implicitly

      override def streamConcurrently(stream1: MonixStream[Unit], stream2: MonixStream[Unit]): MonixStream[Unit] =
        Observable(stream1, stream2).merge
    }
}

/** Execute all the Stream specs using Monix Observable. */
final class MonixObservablesSuite extends StreamSuite(MonixObservablesTestkit)
