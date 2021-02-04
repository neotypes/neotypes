package neotypes.monix.stream

import neotypes.exceptions.CancellationException

import cats.effect.ExitCase
import monix.eval.Task
import monix.reactive.Observable
import org.reactivestreams.Publisher

import scala.collection.compat._

trait MonixStreams {
  implicit final val monixStream: neotypes.Stream.Aux[Observable, Task] =
    new neotypes.Stream[Observable] {
      override final type F[T] = Task[T]

      override final def fromRx[A](publisher: Publisher[A]): Observable[A] =
        Observable.fromReactivePublisher(publisher)

      override def fromF[A](task: Task[A]): Observable[A] =
        Observable.fromTask(task)

      override final def resource[A, B](r: Task[A])(f: A => Observable[B])(finalizer: (A, Option[Throwable]) => Task[Unit]): Observable[B] =
        Observable.resourceCase(acquire = r) {
          case (a, ExitCase.Completed) => finalizer(a, None)
          case (a, ExitCase.Canceled)  => finalizer(a, Some(CancellationException))
          case (a, ExitCase.Error(ex)) => finalizer(a, Some(ex))
        }.flatMap(f)

      override final def map[A, B](sa: Observable[A])(f: A => B): Observable[B] =
        sa.map(f)

      override final def flatMap[A, B](sa: Observable[A])(f: A => Observable[B]): Observable[B] =
        sa.flatMap(f)

      override final def evalMap[A, B](sa: Observable[A])(f: A => Task[B]): Observable[B] =
        sa.mapEval(f)

      override final def collectAs[A, C](sa: Observable[A])(factory: Factory[A, C]): Task[C] =
        sa.foldLeftL(factory.newBuilder)(_ += _).map(_.result())

      override final def single[A](sa: Observable[A]): Task[Option[A]] =
        sa.firstOptionL

      override final def void(s: Observable[_]): Task[Unit] =
        s.completedL
    }
}
