package neotypes

import java.util.concurrent.Executors

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

class FutureAsyncGuaranteeSpec extends AsyncGuaranteeSpec[Future] {
  override def fToEither[T](future: Future[T]): Either[Throwable, T] =
    Try(Await.result(future, Duration.Inf)).toEither

  override final val F: Async[Future] =
    Async.futureAsync(ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor()))
}
