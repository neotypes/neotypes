package neotypes.monix

import java.util.concurrent.Executors

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, AsyncGuaranteeSpec}
import scala.concurrent.duration.Duration

class MonixTaskAsyncGuaranteeSpec extends AsyncGuaranteeSpec[Task] {
  implicit val scheduler: Scheduler = Scheduler(Executors.newSingleThreadExecutor())

  override def fToEither[T](task: Task[T]): Either[Throwable, T] =
    task.attempt.runSyncUnsafe(Duration.Inf)

  override final val F: Async[Task] =
    implicits.monixAsync
}
