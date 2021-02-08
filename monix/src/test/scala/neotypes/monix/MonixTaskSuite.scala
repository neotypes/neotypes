package neotypes.monix

import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.monix.implicits._

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Testkit for monix Task. */
object MonixTaskTestkit extends EffectTestkit[Task] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val scheduler: Scheduler =
        Scheduler(ec)

      override final def fToFuture[A](task: Task[A]): Future[A] =
        task.runToFuture

      override final def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
        Task.parMap2(a, b)((_, _) => ())

      override final val asyncInstance: Async[Task] =
        implicitly
    }
}

/** Execute all the effect specs using Monix Task. */
final class MonixTaskSuite extends EffectSuite(MonixTaskTestkit)
