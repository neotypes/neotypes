package neotypes.monix.stream

import monix.eval.Task
import monix.reactive.Observable

trait MonixStreams {
  implicit final val monixStream: neotypes.Stream.Aux[Observable, Task] =
    new neotypes.Stream[Observable] {
      override final type F[T] = Task[T]

      override final def init[T](value: () => Task[Option[T]]): Observable[T] =
        Observable
          .repeatEvalF(Task.suspend(value()))
          .takeWhile(option => option.isDefined)
          .collect { case Some(t) => t }

      override final def onComplete[T](s: Observable[T])(f: => Task[Unit]): Observable[T] =
        s.guarantee(f)

      override final def fToS[T](f: Task[Observable[T]]): Observable[T] =
        Observable.fromTask(f).flatten
    }
}
