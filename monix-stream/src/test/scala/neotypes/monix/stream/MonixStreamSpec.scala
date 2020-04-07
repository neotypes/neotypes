package neotypes.monix.stream

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, StreamIntegrationSpec}
import neotypes.monix.implicits._
import neotypes.monix.stream.implicits._
import scala.concurrent.Future

class MonixStreamSpec extends StreamIntegrationSpec[MonixStream, Task] { self =>
  implicit val scheduler: Scheduler =
    Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] =
    task.runToFuture

  override def streamToFList[T](stream: MonixStream[T]): Task[List[T]] =
    stream.toListL

  override def F: Async[Task] =
    implicitly

  override def S: neotypes.Stream.Aux[MonixStream,Task] =
    implicitly
}
