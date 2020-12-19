package neotypes.zio.stream

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.zio.ZioTaskTestkit
import neotypes.zio.stream.implicits._

import zio.Task
import zio.stream.ZStream

import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for zio ZStreams. */
object ZioZStreamsTestkit extends StreamTestkit[ZioStream, Task](ZioTaskTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override def streamToFList[A](stream: ZioStream[A]): Task[List[A]] =
        stream.runCollect.map(_.toList)

      override def streamFromF[A](task: Task[A]): ZioStream[A] =
        ZStream.fromEffect(task)

      override final val streamInstance: Stream.Aux[ZioStream, Task] =
        implicitly
    }
}

/** Execute all the stream specs using zio ZStreams. */
final class ZioZStreamsSuite extends StreamSuite(ZioZStreamsTestkit)
