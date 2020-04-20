package neotypes

import scala.concurrent.Future

class FutureAlgorithmSpec extends AlgorithmSpec[Future] {

  def fToFuture[T](f: Future[T]): Future[T] = f

  override final val F: Async[Future] =
    implicitly
}
