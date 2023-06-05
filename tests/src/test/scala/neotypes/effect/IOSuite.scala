package neotypes.cats.effect

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel._
import neotypes.{Async, AsyncSuite, AsyncTestkit}
import neotypes.cats.effect.implicits._

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Async Testkit for cats-effect IO. */
object IOTestkit extends AsyncTestkit[IO] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[A](io: IO[A]): Future[A] =
        io.evalOn(ec).unsafeToFuture()

      override def runConcurrently(a: IO[Unit], b: IO[Unit]): IO[Unit] =
        (a, b).parMapN((_, _) => ())

      override final def cancel[A](a: IO[A]): IO[Unit] =
        a.start.flatMap(_.cancel)

      override final val asyncInstance: Async[IO] =
        implicitly
    }
}

/** Execute all the Async specs using cats-effect IO. */
final class IOSuite extends AsyncSuite(IOTestkit)
