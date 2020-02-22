package neotypes.cats.effect

import cats.effect.IO
import neotypes.{Async, AsyncGuaranteeSpec}

class IOAsyncGuaranteeSpec extends AsyncGuaranteeSpec[IO] {
  override def fToEither[T](io: IO[T]): Either[Throwable, T] =
    io.attempt.unsafeRunSync()

  override final val F: Async[IO] =
    implicits.IOAsync
}
