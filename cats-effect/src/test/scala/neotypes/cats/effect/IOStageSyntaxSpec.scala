package neotypes.cats.effect

import cats.effect.IO
import neotypes.Async
import neotypes.cats.effect.implicits._
import neotypes.internal.syntax.StageSyntaxSpec
import scala.concurrent.Future

class IOStageSyntaxSpec extends StageSyntaxSpec[IO] {
  override def fToFuture[T](io: IO[T]): Future[T] =
    io.unsafeToFuture()

  override final val F: Async[IO] =
    implicitly
}
