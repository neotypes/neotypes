package neotypes.monix

import neotypes.exceptions.CancellationException

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

      override final def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Task[A] =
        Task.async(cb)

      override final def delay[A](t: => A): Task[A] =
        Task.delay(t)

      override final def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] =
        m.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable,A]): Task[A] =
        Task.suspend(Task.fromEither(e))

      override final def guarantee[A, B](fa: Task[A])
                                        (f: A => Task[B])
                                        (finalizer: (A, Option[Throwable]) => Task[Unit]): Task[B] =
        Resource.makeCase(fa) {
          case (a, ExitCase.Completed) => finalizer(a, None)
          case (a, ExitCase.Canceled)  => finalizer(a, Some(CancellationException))
          case (a, ExitCase.Error(ex)) => finalizer(a, Some(ex))
        }.use(f)

      override final def map[A, B](m: Task[A])(f: A => B): Task[B] =
        m.map(f)

      override final def resource[A](input: => A)(close: A => Task[Unit]): Resource[Task, A] =
        Resource.make(delay(input))(close)
    }
}
