package neotypes.zio

import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive
import neotypes.{Async, TransactIntegrationSpec}
import scala.concurrent.Future

class ZioTaskTransactSpec extends TransactIntegrationSpec[Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(self.executionContext)
  }

  override def fToFuture[T](task: Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override final val F: Async[zio.Task] =
    implicits.zioAsync
}
