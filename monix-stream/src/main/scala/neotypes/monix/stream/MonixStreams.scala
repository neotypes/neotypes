package neotypes.monix.stream

import monix.eval.Task
import monix.reactive.Observable

trait MonixStreams {
  implicit final val monixStream: neotypes.Stream.Aux[Observable, Task] =
    new neotypes.Stream[Observable] {
      override type F[T] = Task[T]

      override def init[T](value: () => Task[Option[T]]): Observable[T] =
        Observable
          .repeatEvalF(Task.suspend(value()))
          .takeWhile(option => option.isDefined)
          .collect { case Some(t) => t }

      override def onComplete[T](s: Observable[T])(f: => Task[Unit]): Observable[T] =
        s.guarantee(f)

      override def fToS[T](f: Task[Observable[T]]): Observable[T] =
        Observable.fromTask(f).flatten
    }
}
