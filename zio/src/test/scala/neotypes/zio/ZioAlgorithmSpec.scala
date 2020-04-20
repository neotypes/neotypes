package neotypes.zio

import neotypes.{AlgorithmSpec, Async}
import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive
import neotypes.zio.implicits._

import scala.concurrent.Future

class ZioAlgorithmSpec extends AlgorithmSpec[Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(self.executionContext)
  }

  override def fToFuture[T](task: zio.Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override final val F: Async[zio.Task] =
    implicitly
}
