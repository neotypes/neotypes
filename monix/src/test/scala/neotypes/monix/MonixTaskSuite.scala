package neotypes.monix

import neotypes.{Async, AsyncSuite, AsyncTestkit}
import neotypes.monix.implicits._

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Async Testkit for Monix Task. */
object MonixTaskTestkit extends AsyncTestkit[Task] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val scheduler: Scheduler =
        Scheduler(ec)

      override final def fToFuture[A](task: Task[A]): Future[A] =
        task.runToFuture

      override final def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
        Task.parMap2(a, b)((_, _) => ())

      override final def cancel[A](f: Task[A]): Task[Unit] =
        f.start.flatMap(_.cancel)

      override final val asyncInstance: Async[Task] =
        implicitly
    }
}

/** Execute all the Async specs using Monix Task. */
final class MonixTaskSuite extends AsyncSuite(MonixTaskTestkit)
