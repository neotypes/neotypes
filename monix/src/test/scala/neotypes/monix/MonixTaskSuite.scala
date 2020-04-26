package neotypes.monix

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.monix.implicits._
import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Teskit for monix Task. */
object MonixTaskTestkit extends EffectTestkit[Task] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val scheduler: Scheduler =
        Scheduler(ec)

      override final def fToFuture[T](task: Task[T]): Future[T] =
        task.runToFuture

      override final val asyncInstance: Async[Task] =
        implicitly
    }
}

/** Execute all the effect specs using IO. */
final class MonixTaskSuite extends EffectSuite(MonixTaskTestkit)
