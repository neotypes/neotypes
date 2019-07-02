package neotypes.zio

import zio.Task

trait Zio {
  implicit final val zioAsync: neotypes.Async[Task] =
    new neotypes.Async[Task] {
      override def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.effectAsync { zioCB =>
          cb { e =>
            zioCB(Task.fromEither(e))
          }
        }

      override def delay[A](t: => A): zio.Task[A] =
        Task.succeedLazy(t)

      override def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.catchSome(f)

      override def failed[T](e: Throwable): Task[T] =
        Task.fail(e)

      override def suspend[A](t: => zio.Task[A]): zio.Task[A] =
        Task.suspend(t)
    }
}
