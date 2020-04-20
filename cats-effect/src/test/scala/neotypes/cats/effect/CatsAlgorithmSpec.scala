package neotypes.cats.effect

import cats.effect.IO
import neotypes.{AlgorithmSpec, Async}
import neotypes.cats.effect.implicits._

import scala.concurrent.Future

class CatsAlgorithmSpec extends AlgorithmSpec[IO] {
  override def fToFuture[T](io: IO[T]): Future[T] =
    io.unsafeToFuture()

  override final val F: Async[IO] =
    implicitly
}
