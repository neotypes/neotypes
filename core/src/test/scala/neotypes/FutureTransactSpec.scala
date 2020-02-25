package neotypes

import scala.concurrent.Future

class FutureTransactSpec extends TransactIntegrationSpec[Future] {
  override def fToFuture[T](future: Future[T]): Future[T] =
    future

  override final val F: Async[Future] =
    implicitly
}
