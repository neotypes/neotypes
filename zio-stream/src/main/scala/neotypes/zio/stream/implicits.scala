package neotypes.zio.stream

import zio.Task
import zio.stream.ZStream

object implicits {
  implicit val zioStream: neotypes.Stream.Aux[ZioStream, Task] =
    new neotypes.Stream[ZioStream] {
      override type F[T] = Task[T]

      override def init[T](value: () => Task[Option[T]]): ZioStream[T] =
        ZStream.unfoldM(()) { _: Unit =>
          value().map { optional =>
            optional.map { v =>
              v -> ()
            }
          }
        }

      override def onComplete[T](s: ZioStream[T])(f: => Task[Unit]): ZioStream[T] =
        ZStream.flatten(
          ZStream.bracket(Task(s)) { _ =>
            f.orDie
          }
        )

      override def fToS[T](f: Task[ZioStream[T]]): ZioStream[T] =
        ZStream.flatten(ZStream.fromEffect(f))
    }
}
