package neotypes.future

import neotypes.{Async, AsyncSuite, AsyncTestkit}
import org.scalatest.Assertions

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Async Testkit for Scala Future. */
object FutureTestkit extends AsyncTestkit[Future] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[A](future: Future[A]): Future[A] =
        future

      override final def runConcurrently(a: Future[Unit], b: Future[Unit]): Future[Unit] =
        for (_ <- a; _ <- b) yield () // Because Futures are eager, they are already running concurrently.

      override final def cancel[A](fa: Future[A]): Future[Unit] =
        Future(Assertions.cancel(message = "Futures don't support cancellation"))

      override final val asyncInstance: Async[Future] =
        implicitly
    }
}

/** Execute all the Async specs using Scala Future. */
final class FutureSuite extends AsyncSuite(FutureTestkit)
