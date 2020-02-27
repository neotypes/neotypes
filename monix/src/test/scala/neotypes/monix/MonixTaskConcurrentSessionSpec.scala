package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, ConcurrentSessionSpec}
import neotypes.monix.implicits._
import scala.concurrent.Future

class MonixTaskConcurrentSessionSpec extends ConcurrentSessionSpec[Task] { self =>
  implicit val scheduler: Scheduler =
    Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] =
    task.runToFuture

  override def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
    Task.parMap2(a, b) { (_, _) => () }

  override def F: Async[Task] =
    implicitly
}
