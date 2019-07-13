package neotypes.monix

import cats.effect.Resource
import monix.eval.Task

trait Monix {
  private[neotypes] final type TaskResource[A] = Resource[Task, A]

  implicit final val monixAsync: neotypes.Async.Aux[Task, TaskResource] =
    new neotypes.Async[Task] {
      override final type R[A] = Resource[Task, A]

      override final def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.async(cb)

      override final def failed[T](e: Throwable): Task[T] =
        Task.raiseError(e)

      override final def delay[A](t: => A): Task[A] =
        Task.delay(t)

      override final def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override final def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override final def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.onErrorRecoverWith(f)

      override final def resource[A](input: Task[A])(close: A => Task[Unit]): Resource[Task, A] =
        Resource.make(input)(close)
    }
}
