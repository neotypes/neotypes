package neotypes.zio.stream

import neotypes.{Async, StreamIntegrationSpec}
import neotypes.zio.implicits._
import neotypes.zio.stream.implicits._
import scala.concurrent.Future
import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive

class ZioStreamSpec extends StreamIntegrationSpec[ZioStream, Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(self.executionContext)
  }

  override def fToFuture[T](task: Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override def streamToFList[T](stream: ZioStream[T]): zio.Task[List[T]] =
    stream.runCollect

  override def F: Async[zio.Task] =
    implicitly

  override def S: neotypes.Stream.Aux[ZioStream,zio.Task] =
    implicitly
}
