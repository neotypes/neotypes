package neotypes.zio

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

      override final def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.effectAsync { zioCB =>
          cb { e =>
            zioCB(Task.fromEither(e))
          }
        }

      override final def delay[A](t: => A): zio.Task[A] =
        Task.effectTotal(t)

      override final def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override final def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override def guarantee[A, B](fa: zio.Task[A])
                                  (f: A => zio.Task[B])
                                  (finalizer: (A, Option[Throwable]) => zio.Task[Unit]): zio.Task[B] =
        Managed.makeExit(fa) {
          case (a, Exit.Failure(cause)) => cause.failureOrCause match {
            case Left(ex: Throwable)    => finalizer(a, Some(ex)).ignore
            case _                      => finalizer(a, None).ignore
          }
          case (a, _)                   => finalizer(a, None).orDie
        }.use(f).absorbWith(identity)

      override def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.catchSome(f)

      override final def failed[T](e: Throwable): Task[T] =
        Task.fail(e)

      override def resource[A](input: => A)(close: A => Task[Unit]): Managed[Throwable, A] =
        Managed.make(Task.effectTotal(input))(a => close(a).orDie)
    }
}
