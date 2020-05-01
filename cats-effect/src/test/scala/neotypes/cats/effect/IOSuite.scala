package neotypes.cats.effect

import cats.effect.{ContextShift, IO}
import cats.syntax.parallel._
import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.cats.effect.implicits._
import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Teskit for cats-effect IO. */
object IOTestkit extends EffectTestkit[IO] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      implicit val cs: ContextShift[IO] =
        IO.contextShift(ec)

      override final def fToFuture[T](io: IO[T]): Future[T] =
        io.unsafeToFuture()

      override def runConcurrently(a: IO[Unit], b: IO[Unit]): IO[Unit] =
        (a, b).parMapN { (_, _) => () }

      override final val asyncInstance: Async[IO] =
        implicitly
    }
}

/** Execute all the effect specs using IO. */
final class IOSuite extends EffectSuite(IOTestkit)
