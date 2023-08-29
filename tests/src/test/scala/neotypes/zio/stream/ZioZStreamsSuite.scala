package neotypes.zio.stream

import neotypes.{Stream, StreamSuite, StreamTestkit}
import neotypes.zio.ZioTaskTestkit
import neotypes.zio.stream.implicits._
import zio.Task

import scala.concurrent.ExecutionContext

/** Implementation of the Stream Testkit for ZIO ZStreams. */
object ZioZStreamsTestkit extends StreamTestkit[ZioStream, Task](ZioTaskTestkit) {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final val streamInstance: Stream.Aux[ZioStream, Task] =
        implicitly

      override def streamConcurrently(stream1: ZioStream[Unit], stream2: ZioStream[Unit]): ZioStream[Unit] =
        stream1.merge(stream2)
    }
}

/** Execute all the Stream specs using ZIO ZStreams. */
final class ZioZStreamsSuite extends StreamSuite(ZioZStreamsTestkit)
