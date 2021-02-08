package neotypes.zio.stream

import neotypes.exceptions.CancellationException

import zio.{Exit, Task}
import zio.stream.{ZSink, ZStream}
import zio.interop.reactivestreams.Adapters
import org.reactivestreams.Publisher

import scala.collection.compat._

trait ZioStreams {
  implicit final val zioStream: neotypes.Stream.Aux[ZioStream, Task] =
    new neotypes.Stream[ZioStream] {
      override final type F[T] = Task[T]

      override final def fromRx[A](publisher: Publisher[A]): ZioStream[A] =
        Adapters.publisherToStream(publisher, bufferSize = 16)

      override def fromF[A](task: Task[A]): ZioStream[A] =
        ZStream.fromEffect(task)

      override final def resource[A, B](r: Task[A])(f: A => ZioStream[B])(finalizer: (A, Option[Throwable]) => Task[Unit]): ZioStream[B] =
        ZStream.bracketExit(acquire = r) {
          case (a, Exit.Failure(cause))          => cause.failureOrCause match {
            case Left(ex: Throwable)             => finalizer(a, Some(ex)).ignore
            case Right(c) if (c.interruptedOnly) => finalizer(a, Some(CancellationException)).ignore
            case _                               => finalizer(a, None).ignore
          }
          case (a, _)                            => finalizer(a, None).orDie
        }.flatMap(f)

      override final def map[A, B](sa: ZioStream[A])(f: A => B): ZioStream[B] =
        sa.map(f)

      override final def flatMap[A, B](sa: ZioStream[A])(f: A => ZioStream[B]): ZioStream[B] =
        sa.flatMap(f)

      override final def evalMap[A, B](sa: ZioStream[A])(f: A => Task[B]): ZioStream[B] =
        sa.mapM(f)

      override final def collectAs[A, C](sa: ZioStream[A])(factory: Factory[A, C]): Task[C] =
        sa.run(ZSink.foldLeftChunks(factory.newBuilder)(_ ++= _)).map(_.result())

      override final def single[A](sa: ZioStream[A]): Task[Option[A]] =
        sa.runHead

      override final def void(s: ZioStream[_]): Task[Unit] =
        s.runDrain
    }
}
