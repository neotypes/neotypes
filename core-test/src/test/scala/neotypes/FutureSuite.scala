package neotypes

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Testkit for scala Future. */
object FutureTestkit extends EffectTestkit[Future] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[A](future: Future[A]): Future[A] =
        future

      override final def runConcurrently(a: Future[Unit], b: Future[Unit]): Future[Unit] =
        for (_ <- a; _ <- b) yield () // Because Futures are eager, they are already running concurrently.

      override final def cancel[A](fa: Future[A]): Future[Unit] =
        Future.successful(()) //Not Supported

      override final val asyncInstance: Async[Future] =
        implicitly
    }
}

/** Execute all the effect specs using Future. */
final class FutureSuite extends EffectSuite(FutureTestkit)
