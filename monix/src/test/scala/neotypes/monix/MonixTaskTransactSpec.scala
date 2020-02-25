package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, TransactIntegrationSpec}
import neotypes.monix.implicits._
import scala.concurrent.Future

class MonixTaskTransactSpec extends TransactIntegrationSpec[Task] { self =>
  implicit val scheduler: Scheduler =
    Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] =
    task.runToFuture

  override final val F: Async[Task] =
    implicitly
}
