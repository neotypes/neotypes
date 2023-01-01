package neotypes.monix.stream

import neotypes.model.exceptions.CancellationException

import cats.effect.ExitCase
import monix.eval.Task
import monix.reactive.Observable
import org.reactivestreams.FlowAdapters.toPublisher

import java.util.concurrent.Flow.Publisher
import scala.collection.Factory

trait MonixStreams {
  implicit final val monixStream: neotypes.Stream.Aux[Observable, Task] =
    new neotypes.Stream[Observable] {
      override final type F[T] = Task[T]

      override final def fromPublisher[A](publisher: => Publisher[A], chunkSize: Int): Observable[A] =
        Observable.eval(toPublisher(publisher)).flatMap { p =>
          Observable.fromReactivePublisher(p, chunkSize)
        }

      override final def append[A, B >: A](oa: Observable[A], ob: Observable[B]): Observable[B] =
        oa ++ ob

      override final def fromF[A](task: Task[A]): Observable[A] =
        Observable.fromTask(task)

      override final def guarantee[A, B](r: Task[A])
                                        (f: A => Observable[B])
                                        (finalizer: (A, Option[Throwable]) => Task[Unit]): Observable[B] =
        Observable.resourceCase(acquire = r) {
          case (a, ExitCase.Completed) => finalizer(a, None)
          case (a, ExitCase.Canceled)  => finalizer(a, Some(CancellationException))
          case (a, ExitCase.Error(ex)) => finalizer(a, Some(ex))
        }.flatMap(f)

      override final def map[A, B](oa: Observable[A])(f: A => B): Observable[B] =
        oa.map(f)

      override final def flatMap[A, B](oa: Observable[A])(f: A => Observable[B]): Observable[B] =
        oa.flatMap(f)

      override final def evalMap[A, B](oa: Observable[A])(f: A => Task[B]): Observable[B] =
        oa.mapEval(f)

      override final def collect[A, B](oa: Observable[A])(pf: PartialFunction[A, B]): Observable[B] =
        oa.collect(pf)

      override final def collectAs[A, C](oa: Observable[A])(factory: Factory[A, C]): Task[C] =
        oa.foldLeftL(factory.newBuilder)(_ += _).map(_.result())

      override final def single[A](oa: Observable[A]): Task[Option[A]] =
        oa.firstOptionL

      override final def void[A](o: Observable[A]): Task[Unit] =
        o.completedL
    }
}
