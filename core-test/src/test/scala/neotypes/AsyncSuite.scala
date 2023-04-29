package neotypes

import org.scalatest.{AsyncTestSuite, Suites}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete effect. */
abstract class AsyncTestkit[F[_]](implicit ct: ClassTag[F[_]]) {
  final val asyncName: String = ct.runtimeClass.getCanonicalName

  trait Behaviour {
    def fToFuture[A](fa: F[A]): Future[A]
    def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
    def asyncInstance: Async[F]
    def cancel[A](fa: F[A]): F[Unit]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing effect specs. */
abstract class BaseAsyncSpec[F[_]](effectTestkit: AsyncTestkit[F]) extends AsyncTestSuite {
  protected final val asyncName: String =
    effectTestkit.asyncName

  private final val behaviour: effectTestkit.Behaviour =
    effectTestkit.createBehaviour(this.executionContext)

  protected final def fToFuture[A](f: F[A]): Future[A] =
    behaviour.fToFuture(f)

  protected final def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit] =
    behaviour.runConcurrently(a, b)

  protected final def cancel[A](fa: F[A]): F[Unit] =
    behaviour.cancel(fa)

  protected implicit final val F: Async[F] =
    behaviour.asyncInstance
}

/** Provides an Driver[F] instance for asynchronous tests. */
abstract class AsyncDriverProvider[F[_]](testkit: AsyncTestkit[F]) extends BaseAsyncSpec[F](testkit) with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
  override protected final type DriverType = AsyncDriver[F]
  override protected final type TransactionType = AsyncTransaction[F]

  override protected final lazy val driver: DriverType =
    AsyncDriver[F](self.neoDriver)

  override protected final def transact[T](driver: DriverType)(txF: TransactionType => F[T]): F[T] =
    driver.transact(txF)
}

/** Group all the effect specs into one big suite, which can be called for each effect. */
abstract class AsyncSuite[F[_]](testkit: AsyncTestkit[F]) extends Suites(
  new AsyncDriverSpec(testkit),
  new AsyncDriverTransactSpec(testkit),
  new AsyncGuaranteeSpec(testkit),
  new AsyncAlgorithmSpec(testkit)
)
