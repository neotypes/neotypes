package neotypes.fs2

import cats.effect.IO
import neotypes.{Async, StreamIntegrationSpec}
import neotypes.cats.effect.implicits._
import neotypes.fs2.implicits._
import neotypes.fs2.implicits.Fs2IoStream
import scala.concurrent.Future

class Fs2StreamSpec extends StreamIntegrationSpec[Fs2IoStream, IO] {
  override def fToFuture[T](io: IO[T]): Future[T] =
    io.unsafeToFuture()

  override def streamToFList[T](stream: Fs2IoStream[T]): IO[List[T]] =
    stream.compile.toList

  override final val F: Async[IO] =
    implicitly

  override final val S: neotypes.Stream.Aux[Fs2IoStream, IO] =
    implicitly
}
