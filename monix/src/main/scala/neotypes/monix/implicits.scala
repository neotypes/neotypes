package neotypes.monix

import monix.eval.Task

object implicits {
  implicit val monixAsync: neotypes.Async[Task] =
    new neotypes.Async[Task] {
      override def async[T](cb: (Either[Throwable, T] => Unit) => Unit): Task[T] =
        Task.async(cb)

      override def flatMap[T, U](m: Task[T])(f: T => Task[U]): Task[U] =
        m.flatMap(f)

      override def map[T, U](m: Task[T])(f: T => U): Task[U] =
        m.map(f)

      override def recoverWith[T, U >: T](m: Task[T])(f: PartialFunction[Throwable, Task[U]]): Task[U] =
        m.onErrorRecoverWith(f)

      override def failed[T](e: Throwable): Task[T] =
        Task.raiseError(e)

      override def success[T](t: => T): Task[T] =
        Task(t)
    }
}
