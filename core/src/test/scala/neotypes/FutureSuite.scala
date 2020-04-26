package neotypes

import scala.concurrent.{ExecutionContext, Future}

/** Implementation of the Effect Teskit for scala Future. */
object FutureTestkit extends EffectTestkit[Future] {
  override def createBehaviour(implicit ec: ExecutionContext): Behaviour =
    new Behaviour {
      override final def fToFuture[T](future: Future[T]): Future[T] =
        future

      override final val asyncInstance: Async[Future] =
        implicitly
    }
}

/** Execute all the effect specs using Future. */
final class FutureSuite extends EffectSuite(FutureTestkit)
