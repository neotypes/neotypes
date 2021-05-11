package neotypes.fs2

import neotypes.exceptions.CancellationException

import cats.effect.{Async, Resource}
import fs2.Stream
import org.reactivestreams.Publisher

import scala.collection.compat.Factory

trait Fs2Streams {
  implicit final def fs2Stream[_F[_]](implicit F: Async[_F]): neotypes.Stream.Aux[Fs2FStream[_F]#T, _F] =
    new neotypes.Stream[Fs2FStream[_F]#T] {
      override final type F[A] = _F[A]

      override final def fromRx[A](publisher: Publisher[A]): Stream[F, A] =
        fs2.interop.reactivestreams.fromPublisher(publisher)

      override def fromF[A](fa: F[A]): Stream[F, A] =
        Stream.eval(fa)

      override final def resource[A, B](r: F[A])(f: A => Stream[F, B])(finalizer: (A, Option[Throwable]) => F[Unit]): Stream[F, B] =
        Stream.bracketCase(acquire = r) {
          case (a, Resource.ExitCase.Succeeded)   => finalizer(a, None)
          case (a, Resource.ExitCase.Canceled)    => finalizer(a, Some(CancellationException))
          case (a, Resource.ExitCase.Errored(ex)) => finalizer(a, Some(ex))
        }.flatMap(f)

      override final def map[A, B](sa: Stream[F, A])(f: A => B): Stream[F, B] =
        sa.map(f)

      override final def flatMap[A, B](sa: Stream[F, A])(f: A => Stream[F, B]): Stream[F, B] =
        sa.flatMap(f)

      override final def evalMap[A, B](sa: Stream[F, A])(f: A => F[B]): Stream[F, B] =
        sa.evalMap(f)

      override final def collectAs[A, C](sa: Stream[F, A])(factory: Factory[A, C]): F[C] = {
        // Thanks to Jasper Moeys (@Jasper-M) for providing this workaround.
        // We are still not sure this is totally safe, if you find a bug please let's us know.
        type CC[x] = C
        val f: Factory[A, CC[A]] = factory
        sa.compile.to(f)
      }

      override final def single[A](sa: Stream[F, A]): F[Option[A]] =
        sa.take(1).compile.last

      override final def void(s: Stream[F, _]): F[Unit] =
        s.compile.drain
    }
}
