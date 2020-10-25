package neotypes.fs2

import cats.effect.{ConcurrentEffect, ExitCase}
import fs2.Stream
import org.reactivestreams.Publisher

import scala.collection.compat.Factory

trait Fs2Streams {
  implicit final def fs2Stream[_F[_]](implicit F: ConcurrentEffect[_F]): neotypes.Stream.Aux[Fs2FStream[_F]#T, _F] =
    new neotypes.Stream[Fs2FStream[_F]#T] {
      override final type F[T] = _F[T]

      // Legacy module ------------------------------------------------------------
      override final def init[T](value: () => F[Option[T]]): Stream[F, T] =
        Stream.repeatEval(F.suspend(value())).unNoneTerminate

      override final def onComplete[T](s: Stream[F, T])(f: => F[Unit]): Stream[F, T] =
        s.onFinalize(f)

      override final def fToS[T](f: F[Stream[F, T]]): Stream[F, T] =
        Stream.eval(f).flatten
      // --------------------------------------------------------------------------


      // New (Rx) module ----------------------------------------------------------
      override final def fromRx[A](publisher: Publisher[A]): Stream[F, A] =
        fs2.interop.reactivestreams.fromPublisher(publisher)

      override final def fromF[A](fa: F[A]): Stream[F, A] =
        Stream.eval(fa)

      override final def resource[A](r: F[A])(finalizer: (A, Option[Throwable]) => F[Unit]): Stream[F, A] =
        Stream.bracketCase(acquire = r) {
          case (a, ExitCase.Completed | ExitCase.Canceled) => finalizer(a, None)
          case (a, ExitCase.Error(ex))                     => finalizer(a, Some(ex))
        }

      override final def map[A, B](sa: Stream[F, A])(f: A => B): Stream[F, B] =
        sa.map(f)

      override final def flatMap[A, B](sa: Stream[F, A])(f: A => Stream[F, B]): Stream[F, B] =
        sa.flatMap(f)

      override final def evalMap[A, B](sa: Stream[F, A])(f: A => F[B]): Stream[F, B] =
        sa.evalMap(f)

      override final def collectAs[C, A](sa: Stream[F, A])(factory: Factory[A, C]): F[C] = {
        // Thanks to Jasper Moeys (@Jasper-M) for providing this workaround.
        // We are still not sure this is totally safe, if you find a bug please let's us know.
        type CC[x] = C
        val f: Factory[A, CC[A]] = factory
        sa.compile.to(f)
      }

      override final def single[A](sa: Stream[F, A]): F[A] =
        sa.take(1).compile.lastOrError

      override final def void(s: fs2.Stream[F, _]): F[Unit] =
        s.compile.drain
      // --------------------------------------------------------------------------
    }
}
