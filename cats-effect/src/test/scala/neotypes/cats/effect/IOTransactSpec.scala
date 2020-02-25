package neotypes.cats.effect

import cats.effect.IO
import neotypes.{Async, TransactIntegrationSpec}
import scala.concurrent.Future

class IOTransactSpec extends TransactIntegrationSpec[IO] {
  override def fToFuture[T](io: IO[T]): Future[T] =
    io.unsafeToFuture()

  override final val F: Async[IO] =
    implicits.IOAsync
}
