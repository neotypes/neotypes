package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{AlgorithmSpec, Async}
import neotypes.monix.implicits._
import scala.concurrent.Future

class MonixAlgorithmSpec extends AlgorithmSpec[Task] { self =>
  implicit val scheduler: Scheduler =
    Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] =
    task.runToFuture

  override final val F: Async[Task] =
    implicitly
}
