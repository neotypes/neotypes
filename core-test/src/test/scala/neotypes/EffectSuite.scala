package neotypes

import org.scalatest.{AsyncTestSuite, Suites}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import izumi.reflect.TagK

/** Testkit used to write specs abstracted from any concrete effect. */
abstract class EffectTestkit[F[_]](implicit ct: TagK[F]) {
  final val effectName: String = ct.tag.shortName

  trait Behaviour {
    def fToFuture[A](fa: F[A]): Future[A]
    def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
    def asyncInstance: Async[F]
    def cancel[A](fa: F[A]): F[Unit]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing effect specs. */
abstract class BaseEffectSpec[F[_]](effectTestkit: EffectTestkit[F]) extends AsyncTestSuite {
  protected final val effectName: String =
    effectTestkit.effectName

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
abstract class AsyncDriverProvider[F[_]](testkit: EffectTestkit[F]) extends BaseEffectSpec[F](testkit) with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
  override final type DriverType = Driver[F]

  override protected final lazy val driver: DriverType =
    Driver[F](self.neoDriver)
}

/** Group all the effect specs into one big suite, which can be called for each effect. */
abstract class EffectSuite[F[_]](testkit: EffectTestkit[F]) extends Suites(
  new AsyncDriverSpec(testkit),
  new AsyncGuaranteeSpec(testkit)
)
