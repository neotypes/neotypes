package neotypes.zio

import neotypes.{Async, ConcurrentSessionSpec}
import neotypes.zio.implicits._
import scala.concurrent.Future
import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive

class ZioTaskConcurrentSessionSpec extends ConcurrentSessionSpec[Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(self.executionContext)
  }

  override def fToFuture[T](task: Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
    a.zipPar(b).map { _ => () }

  override def F: Async[Task] =
    implicitly
}
