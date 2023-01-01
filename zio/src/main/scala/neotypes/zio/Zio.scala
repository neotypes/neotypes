package neotypes.zio

import neotypes.model.exceptions.CancellationException

import zio.{Exit, Task, ZIO}
import java.util.concurrent.CompletionStage

trait Zio {
  implicit final val instance: neotypes.Async.Aux[Task, ZioResource] =
    new neotypes.Async[Task] {
      override final type R[A] = ZioResource[A]

      override final def fromCompletionStage[A](completionStage: => CompletionStage[A]): Task[A] =
        ZIO.fromCompletionStage(completionStage)

      override final def delay[A](a: => A): Task[A] =
        ZIO.attempt(a)

      override final def flatMap[A, B](task: Task[A])(f: A => Task[B]): Task[B] =
        task.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable, A]): Task[A] =
        ZIO.fromEither(e)

      override def guarantee[A, B](task: zio.Task[A])
                                  (f: A => zio.Task[B])
                                  (finalizer: (A, Option[Throwable]) => zio.Task[Unit]): Task[B] =
          ZIO.scoped {
            ZIO.acquireReleaseExit(task) {
              case (a, Exit.Failure(cause)) =>
                cause.failureOrCause match {
                  case Left(ex: Throwable) =>
                    finalizer(a, Some(ex)).orDie

                  case Right(c) if (c.isInterruptedOnly) =>
                    finalizer(a, Some(CancellationException)).orDie

                  case _ =>
                    finalizer(a, None).orDie
                }

              case (a, _) =>
                finalizer(a, None).orDie
            }.flatMap(f)
          }

      override final def map[A, B](task: Task[A])(f: A => B): Task[B] =
        task.map(f)

      override final def mapError[A](task: Task[A])(f: Throwable => Throwable): Task[A] =
        task.mapError(f)

      override def resource[A](input: => A)(close: A => Task[Unit]): ZioResource[A] =
        ZIO.acquireRelease(ZIO.attempt(input))(a => close(a).orDie)
    }
}
