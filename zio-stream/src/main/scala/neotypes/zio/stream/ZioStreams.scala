package neotypes.zio.stream

import zio.Task
import zio.stream.ZStream

trait ZioStreams {
  implicit final val zioStream: neotypes.Stream.Aux[ZioStream, Task] =
    new neotypes.Stream[ZioStream] {
      override final type F[T] = Task[T]

      override final def init[T](value: () => Task[Option[T]]): ZioStream[T] =
        ZStream.unfoldM(()) { _: Unit =>
          value().map { optional =>
            optional.map { v =>
              (v, ())
            }
          }
        }

      override final def onComplete[T](s: ZioStream[T])(f: => Task[Unit]): ZioStream[T] =
        ZStream.bracket(Task(s)) { _ =>
          f.orDie
        }.flatten

      override final def fToS[T](f: Task[ZioStream[T]]): ZioStream[T] =
        ZStream.fromEffect(f).flatten
    }
}
