package neotypes.zio

import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.zio.implicits._

import zio.{Executor, Runtime, Task, Unsafe}

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Testkit for zio Task. */
object ZioTaskTestkit extends EffectTestkit[Task] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      val runtime =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.unsafe.fromLayer(
            Runtime.setExecutor(
              Executor.fromExecutionContext(ec)
            )
          )
        }

      override final def fToFuture[A](task: Task[A]): Future[A] =
        Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe.runToFuture(task)
        }

      override final def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
        a.zipPar(b).map(_ => ())

      override final def cancel[A](fa: Task[A]): Task[Unit] =
        fa.fork.map(_.interrupt).flatten.unit

      override final val asyncInstance: Async[Task] =
        implicitly
    }
}

/** Execute all the effect specs using ZIO Task. */
final class ZioTaskSuite extends EffectSuite(ZioTaskTestkit)
