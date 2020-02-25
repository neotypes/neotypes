package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, AsyncIntegrationSpec}
import neotypes.monix.implicits._
import scala.concurrent.Future

class MonixAsyncSpec extends AsyncIntegrationSpec[Task] { self =>
  implicit val scheduler: Scheduler =
    Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] =
    task.runToFuture

  override final val F: Async[Task] =
    implicitly
}
