package neotypes.zio

import neotypes.exceptions.CancellationException

import zio.{Exit, Managed, Task}

trait Zio {
  implicit final def zioAsync: neotypes.Async.Aux[Task, Zio.ZioResource] =
    Zio.instance
}

object Zio {
  private[neotypes] final type ZioResource[A] = Managed[Throwable, A]

  private final val instance: neotypes.Async.Aux[Task, ZioResource] =
    new neotypes.Async[Task] {
      override final type R[A] = Managed[Throwable, A]

      override final def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Task[A] =
        Task.effectAsync { zioCB =>
          cb { e =>
            zioCB(Task.fromEither(e))
          }
        }

      override final def delay[A](t: => A): Task[A] =
        Task.effectTotal(t)

      override final def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] =
        m.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable, A]): Task[A] =
        Task.fromEither(e)

      override def guarantee[A, B](fa: zio.Task[A])
                                  (f: A => zio.Task[B])
                                  (finalizer: (A, Option[Throwable]) => zio.Task[Unit]): zio.Task[B] =
        Managed.makeExit(fa) {
          case (a, Exit.Failure(cause))          => cause.failureOrCause match {
            case Left(ex: Throwable)             => finalizer(a, Some(ex)).ignore
            case Right(c) if (c.interruptedOnly) => finalizer(a, Some(CancellationException)).ignore
            case _                               => finalizer(a, None).ignore
          }
          case (a, _)                            => finalizer(a, None).orDie
        }.use(f).absorbWith(identity)

      override final def map[A, B](m: Task[A])(f: A => B): Task[B] =
        m.map(f)

      override def resource[A](input: => A)(close: A => Task[Unit]): Managed[Throwable, A] =
        Managed.make(delay(input))(a => close(a).orDie)
    }
}
