package neotypes

import neotypes.internal.syntax.StageSyntaxSpec
import org.scalatest.{AsyncTestSuite, Suites}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete effect. */
abstract class EffectTestkit[F[_]](implicit ct: ClassTag[F[_]]) {
  final val effectName: String = ct.runtimeClass.getCanonicalName

  trait Behaviour {
    def fToFuture[T](f: F[T]): Future[T]
    def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
    def asyncInstance: Async[F]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing effect specs. */
abstract class BaseEffectSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncTestSuite { self =>
  protected final val effectName: String =
    testkit.effectName

  private final val behaviour: testkit.Behaviour =
    testkit.createBehaviour(self.executionContext)

  protected final def fToFuture[T](f: F[T]): Future[T] =
    behaviour.fToFuture(f)

  protected final def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit] =
    behaviour.runConcurrently(a, b)

  protected implicit final val F: Async[F] =
    behaviour.asyncInstance
}

/** Group all the effect specs into one big suite, which can be called for each effect. */
abstract class EffectSuite[F[_]](testkit: EffectTestkit[F]) extends Suites(
  new AsyncGuaranteeSpec(testkit),
  new AsyncIntegrationSpec(testkit),
  new BasicSessionSpec(testkit),
  new BasicTransactionSpec(testkit),
  new CompositeTypesSpec(testkit),
  new ConcurrentSessionSpec(testkit),
  new ParameterSpec(testkit),
  new PathSessionSpec(testkit),
  new QueryExecutionSpec(testkit),
  new StageSyntaxSpec(testkit),
  new TransactIntegrationSpec(testkit)
)
