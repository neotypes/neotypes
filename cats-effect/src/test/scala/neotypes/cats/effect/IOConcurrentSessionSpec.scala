package neotypes.cats.effect

import cats.effect.{ContextShift, IO}
import cats.syntax.parallel._
import neotypes.{Async, ConcurrentSessionSpec}
import neotypes.cats.effect.implicits._
import scala.concurrent.Future

class IOConcurrentSessionSpec extends ConcurrentSessionSpec[IO] { self =>
  implicit val cs: ContextShift[IO] =
    IO.contextShift(self.executionContext)

  override def fToFuture[T](io: IO[T]): Future[T] =
    io.unsafeToFuture()

  override def runConcurrently(a: IO[Unit], b: IO[Unit]): IO[Unit] =
    (a, b).parMapN { (_, _) => () }

  override def F: Async[IO] =
    implicitly
}
