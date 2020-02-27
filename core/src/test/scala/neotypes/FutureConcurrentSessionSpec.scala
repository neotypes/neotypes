package neotypes

import scala.concurrent.Future

class FutureConcurrentSessionSpec extends ConcurrentSessionSpec[Future] {
  override def fToFuture[T](future: Future[T]): Future[T] =
    future

  override def runConcurrently(a: Future[Unit], b: Future[Unit]): Future[Unit] =
    for (_ <- a; _ <- b) yield () // Because Futures are eager, they are already running concurrently.

  override def F: Async[Future] =
    implicitly
}
