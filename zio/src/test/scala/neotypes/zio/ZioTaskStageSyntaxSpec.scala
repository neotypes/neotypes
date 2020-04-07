package neotypes.zio

import neotypes.Async
import neotypes.internal.syntax.StageSyntaxSpec
import neotypes.zio.implicits._
import zio.{DefaultRuntime, Task}
import zio.internal.{PlatformLive, Tracing}
import scala.concurrent.Future

class ZioTaskStageSyntaxSpec extends StageSyntaxSpec[Task] { self =>
  val runtime = new DefaultRuntime {
    override val platform =
      PlatformLive
        .fromExecutionContext(self.executionContext)
        .withReportFailure(_ => ())
        .withReportFailure(_ => ())
        .withTracing(Tracing.disabled)
  }

  override def fToFuture[T](task: Task[T]): Future[T] =
    runtime.unsafeRunToFuture(task)

  override final val F: Async[Task] =
    implicitly
}
