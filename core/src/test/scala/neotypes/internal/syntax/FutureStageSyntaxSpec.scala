package neotypes
package internal.syntax

import scala.concurrent.Future

class FutureStageSyntaxSpec extends StageSyntaxSpec[Future] {
  override def fToFuture[T](future: Future[T]): Future[T] =
    future

  override final val F: Async[Future] =
    implicitly
}
