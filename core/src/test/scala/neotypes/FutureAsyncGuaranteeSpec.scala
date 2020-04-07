package neotypes

import java.util.concurrent.Executors

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

class FutureAsyncGuaranteeSpec extends AsyncGuaranteeSpec[Future] {
  implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  override def fToEither[T](future: Future[T]): Either[Throwable, T] =
    Try(Await.result(future, Duration.Inf)).toEither

  override final val F: Async[Future] =
    implicitly
}
