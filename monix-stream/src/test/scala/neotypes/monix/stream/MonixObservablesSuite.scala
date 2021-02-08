package neotypes.monix.stream

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.monix.MonixTaskTestkit
import neotypes.monix.stream.implicits._

import monix.eval.Task

import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for monix observables. */
object MonixObservablesTestkit extends StreamTestkit[MonixStream, Task](MonixTaskTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override def streamToFList[A](stream: MonixStream[A]): Task[List[A]] =
        stream.toListL

      override final val streamInstance: Stream.Aux[MonixStream, Task] =
        implicitly
    }
}

/** Execute all the stream specs using monix observables. */
final class MonixObservablesSuite extends StreamSuite(MonixObservablesTestkit)
