package neotypes.zio

import neotypes.exceptions.CancellationException

import zio.{ ZIO, Task, Scope, Exit }

trait Zio {
  implicit final def zioAsync: neotypes.Async.Aux[Task, Zio.ZioResource] =
    Zio.instance
}
 
object Zio {
  private[neotypes] final type ZioResource[A] = ZIO[Scope, Throwable, A]

  private final val instance: neotypes.Async.Aux[Task, ZioResource] =
    new neotypes.Async[Task] {
      override final type R[A] = ZioResource[A]

      override final def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Task[A] =
        ZIO.async { zioCB =>
          cb { e =>
            zioCB(ZIO.fromEither(e))
          }
        }

      override final def delay[A](t: => A): Task[A] =
        ZIO.attempt(t)

      override final def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] =
        m.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable, A]): Task[A] =
        ZIO.fromEither(e)

      override def guarantee[A, B](fa: zio.Task[A])
                                  (f: A => zio.Task[B])
                                  (finalizer: (A, Option[Throwable]) => zio.Task[Unit]): Task[B] =
          ZIO.scoped {
            ZIO.acquireReleaseExit(fa) {
              case (a, Exit.Failure(cause)) => cause.failureOrCause match {
                case Left(ex: Throwable) => finalizer(a, Some(ex)).orDie
                case Right(c) if (c.isInterruptedOnly) => finalizer(a, Some(CancellationException)).orDie
                case _ => finalizer(a, None).orDie
              }
              case (a, _) => finalizer(a, None).orDie
            }.flatMap(f)
          }

      override final def map[A, B](m: Task[A])(f: A => B): Task[B] =
        m.map(f)

      override def resource[A](input: => A)(close: A => Task[Unit]): ZIO[Scope, Throwable, A] =
         ZIO.acquireRelease(ZIO.attempt(input))(a => close(a).orDie)
    }
}
