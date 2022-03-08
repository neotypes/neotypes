package neotypes

import neotypes.internal.syntax.StageSyntaxSpec

import org.scalatest.{AsyncTestSuite, Suites}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete effect. */
abstract class EffectTestkit[F[_]](implicit ct: ClassTag[F[_]]) {
  final val effectName: String = ct.runtimeClass.getCanonicalName

  trait Behaviour {
    def fToFuture[A](fa: F[A]): Future[A]
    def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
    def asyncInstance: Async[F]
    def cancel[A](fa: F[A]): F[Unit]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing effect specs. */
abstract class BaseEffectSpec[F[_]](effectTestkit: EffectTestkit[F]) extends AsyncTestSuite { self =>
  protected final val effectName: String =
    effectTestkit.effectName

  private final val behaviour: effectTestkit.Behaviour =
    effectTestkit.createBehaviour(self.executionContext)

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
abstract class AsyncDriverProvider[F[_]](testkit: EffectTestkit[F]) extends BaseEffectSpec[F](testkit) with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
  override type DriverType = Driver[F]

  override protected final lazy val driver: DriverType =
    Driver[F](self.neoDriver)

  protected final def executeAsFuture[A](work: DriverType => F[A]): Future[A] =
    fToFuture(work(driver))

  protected final def debugMetrics(): F[Unit] =
    F.map(driver.metrics) { metrics =>
      println(s"METRICS: ${metrics}")
    }
}

/** Group all the effect specs into one big suite, which can be called for each effect. */
abstract class EffectSuite[F[_]](testkit: EffectTestkit[F]) extends Suites(
  new AlgorithmSpec(testkit),
  new AsyncGuaranteeSpec(testkit),
  new AsyncSpec(testkit),
  new AsyncTransactionSpec(testkit),
  new AsyncTransactSpec(testkit),
  new CancelSpec(testkit),
  new CompositeTypesSpec(testkit),
  new ConcurrentDriverSpec(testkit),
  new DriverSpec(testkit),
  new ParameterSpec(testkit),
  new PathSessionSpec(testkit),
  new QueryExecutionSpec(testkit),
  new StageSyntaxSpec(testkit)
)
