package neotypes.zio

import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.zio.implicits._
import scala.concurrent.{ExecutionContext, Future}
import zio.{Runtime, Task}
import zio.internal.Platform

/** Implementation of the Effect Teskit for zio Task. */
object ZioTaskTestkit extends EffectTestkit[Task] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      val runtime = Runtime.default.mapPlatform(_ => Platform.fromExecutionContext(ec))

      override final def fToFuture[T](task: Task[T]): Future[T] =
        runtime.unsafeRunToFuture(task)

      override final def runConcurrently(a: Task[Unit], b: Task[Unit]): Task[Unit] =
        a.zipPar(b).map { _ => () }

      override final val asyncInstance: Async[Task] =
        implicitly
    }
}

/** Execute all the effect specs using IO. */
final class ZioTaskSuite extends EffectSuite(ZioTaskTestkit)
