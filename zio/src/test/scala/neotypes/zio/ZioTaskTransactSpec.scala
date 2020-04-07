package neotypes.zio

import neotypes.{Async, TransactIntegrationSpec}
import neotypes.zio.implicits._
import scala.concurrent.Future
import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive

class ZioTaskTransactSpec extends TransactIntegrationSpec[Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(self.executionContext)
  }

  override def fToFuture[T](task: Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override final val F: Async[zio.Task] =
    implicitly
}
