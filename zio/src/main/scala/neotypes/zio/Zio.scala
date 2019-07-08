package neotypes.zio

import zio.{Task, Managed}

trait Zio {
  private[neotypes] final type ZioResource[A] = Managed[Throwable, A]

  implicit final val zioAsync: neotypes.Async.Aux[Task, ZioResource] =
    new neotypes.Async[Task] {
      override final type R[A] = Managed[Throwable, A]

      override final def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.effectAsync { zioCB =>
          cb { e =>
            zioCB(Task.fromEither(e))
          }
        }

      override final def delay[A](t: => A): zio.Task[A] =
        Task.succeedLazy(t)

      override final def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override final def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.catchSome(f)

      override final def failed[T](e: Throwable): Task[T] =
        Task.fail(e)

      override def resource[A](input: Task[A])(close: A => Task[Unit]): Managed[Throwable, A] =
        Managed.make(input)(a => close(a).orDie)
    }
}
