package neotypes.zio.stream

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.zio.ZioTaskTestkit
import neotypes.zio.stream.implicits._
import scala.concurrent.ExecutionContext
import zio.Task

/** Implementation of the Stream Teskit for zio ZStreams. */
object ZioZStreamsTestkit extends StreamTestkit[ZioStream, Task](ZioTaskTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override def streamToFList[T](stream: ZioStream[T]): Task[List[T]] =
        stream.runCollect

      override final val streamInstance: Stream.Aux[ZioStream, Task] =
        implicitly
    }
}

/** Execute all the stream specs using zio ZStreams. */
final class ZioZStreamsSuite extends StreamSuite(ZioZStreamsTestkit)
