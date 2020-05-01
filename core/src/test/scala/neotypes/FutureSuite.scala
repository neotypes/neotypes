package neotypes

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Teskit for scala Future. */
object FutureTestkit extends EffectTestkit[Future] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[T](future: Future[T]): Future[T] =
        future

      override final def runConcurrently(a: Future[Unit], b: Future[Unit]): Future[Unit] =
        for (_ <- a; _ <- b) yield () // Because Futures are eager, they are already running concurrently.

      override final val asyncInstance: Async[Future] =
        implicitly
    }
}

/** Execute all the effect specs using Future. */
final class FutureSuite extends EffectSuite(FutureTestkit)
