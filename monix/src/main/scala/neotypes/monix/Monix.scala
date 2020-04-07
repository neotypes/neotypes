package neotypes.monix

import cats.effect.{ExitCase, Resource}
import monix.eval.Task

trait Monix {
  implicit final def monixAsync: neotypes.Async.Aux[Task, Monix.TaskResource] =
    Monix.instance
}

object Monix {
  private[neotypes] final type TaskResource[A] = Resource[Task, A]

  private final val instance: neotypes.Async.Aux[Task, TaskResource] =
    new neotypes.Async[Task] {
      override final type R[A] = Resource[Task, A]

      override final def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.async(cb)

      override final def delay[A](t: => A): Task[A] =
        Task.delay(t)

      override final def failed[T](e: Throwable): Task[T] =
        Task.raiseError(e)

      override final def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override final def guarantee[A, B](fa: Task[A])
                                        (f: A => Task[B])
                                        (finalizer: (A, Option[Throwable]) => Task[Unit]): Task[B] =
        Resource.makeCase(fa) {
          case (a, ExitCase.Completed | ExitCase.Canceled) => finalizer(a, None)
          case (a, ExitCase.Error(ex))                     => finalizer(a, Some(ex))
        }.use(f)

      override final def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override final def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.onErrorRecoverWith(f)

      override final def resource[A](input: => A)(close: A => Task[Unit]): Resource[Task, A] =
        Resource.make(Task.delay(input))(close)
    }
}
