package neotypes.zio

import java.util.concurrent.Executors
import neotypes.{Async, AsyncGuaranteeSpec}
import neotypes.zio.implicits._
import scala.concurrent.ExecutionContext
import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive

class ZioTaskAsyncGuaranteeSpec extends AsyncGuaranteeSpec[Task] {
  val runtime = new DefaultRuntime {
    override val platform = PlatformLive.fromExecutionContext(
      ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    )
  }

  override def fToEither[T](task: Task[T]): Either[Throwable, T] =
    runtime.unsafeRun(task.either)

  override final val F: Async[Task] =
    implicitly
}
