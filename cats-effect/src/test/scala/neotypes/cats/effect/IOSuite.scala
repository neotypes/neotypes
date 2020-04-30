package neotypes.cats.effect

import cats.effect.IO
import neotypes.{Async, EffectSuite, EffectTestkit}
import neotypes.cats.effect.implicits._
import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Teskit for cats-effect IO. */
object IOTestkit extends EffectTestkit[IO] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[T](io: IO[T]): Future[T] =
        io.unsafeToFuture()

      override final val asyncInstance: Async[IO] =
        implicitly
    }
}

/** Execute all the effect specs using IO. */
final class IOSuite extends EffectSuite(IOTestkit)
