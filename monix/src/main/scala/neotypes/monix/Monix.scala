package neotypes.monix

import neotypes.model.exceptions.CancellationException

import cats.effect.{ExitCase, Resource}
import monix.eval.Task

import java.util.concurrent.CompletionStage

trait Monix {
  private[neotypes] final type TaskResource[A] = Resource[Task, A]

  implicit final def monixAsync: neotypes.Async.Aux[Task, TaskResource] =
    new neotypes.Async[Task] {
      override final type R[A] = Resource[Task, A]

      override final def fromCompletionStage[A](completionStage: => CompletionStage[A]): Task[A] =
        Task.fromFutureLike(Task.delay(completionStage.toCompletableFuture()))

      override final def delay[A](a: => A): Task[A] =
        Task.delay(a)

      override final def flatMap[A, B](task: Task[A])(f: A => Task[B]): Task[B] =
        task.flatMap(f)

      override final def fromEither[A](e: => Either[Throwable, A]): Task[A] =
        Task.suspend(Task.fromEither(e))

      override final def guarantee[A, B](
        task: Task[A]
      )(
        f: A => Task[B]
      )(
        finalizer: (A, Option[Throwable]) => Task[Unit]
      ): Task[B] =
        Resource
          .makeCase(task) {
            case (a, ExitCase.Completed) => finalizer(a, None)
            case (a, ExitCase.Canceled)  => finalizer(a, Some(CancellationException))
            case (a, ExitCase.Error(ex)) => finalizer(a, Some(ex))
          }
          .use(f)

      override final def map[A, B](task: Task[A])(f: A => B): Task[B] =
        task.map(f)

      override final def mapError[A](task: Task[A])(f: Throwable => Throwable): Task[A] =
        task.onErrorHandleWith(ex => Task.raiseError(f(ex)))

      override final def resource[A](input: => A)(close: A => Task[Unit]): Resource[Task, A] =
        Resource.make(delay(input))(close)
    }
}
