package neotypes.cats.effect

import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.cats.effect.implicits._

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel._

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Testkit for cats-effect IO. */
object IOTestkit extends EffectTestkit[IO] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[A](io: IO[A]): Future[A] =
        io.evalOn(ec).unsafeToFuture()

      override def runConcurrently(a: IO[Unit], b: IO[Unit]): IO[Unit] =
        (a, b).parMapN((_, _) => ())

      override final val asyncInstance: Async[IO] =
        implicitly
    }
}

/** Execute all the effect specs using cats-effect IO. */
final class IOSuite extends EffectSuite(IOTestkit)
