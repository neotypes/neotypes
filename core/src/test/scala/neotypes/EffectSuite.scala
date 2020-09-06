package neotypes

import neotypes.internal.syntax.StageSyntaxSpec
import org.neo4j.{driver => neo4j}
import org.scalatest.{AsyncTestSuite, Suites}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete effect. */
abstract class EffectTestkit[F[_]](implicit ct: ClassTag[F[_]]) {
  final val effectName: String = ct.runtimeClass.getCanonicalName

  trait Behaviour {
    def fToT[T](f: F[T]): T
    def fToFuture[T](f: F[T]): Future[T]
    def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
    def asyncInstance: Async[F]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing effect specs. */
abstract class BaseEffectSpec[F[_]](protected val effectTestkit: EffectTestkit[F]) extends AsyncTestSuite {
  protected final val effectName: String =
    effectTestkit.effectName

  protected final val effectBehaviour: effectTestkit.Behaviour =
    effectTestkit.createBehaviour(this.executionContext)

  protected final def fToT[T](f: F[T]): T =
    effectBehaviour.fToT(f)

  protected final def fToFuture[T](f: F[T]): Future[T] =
    effectBehaviour.fToFuture(f)

  protected final def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit] =
    effectBehaviour.runConcurrently(a, b)

  protected implicit final val F: Async[F] =
    effectBehaviour.asyncInstance
}

/** Provides an asynchronous session for effectual tests. */
abstract class EffectSessionProvider[F[_]] (testkit: EffectTestkit[F]) extends BaseEffectSpec[F](testkit) with SessionProvider[F] { self: BaseIntegrationSpec[F] =>
  override protected final val sessionType: String = "AsyncSession"

  override protected final lazy val neotypesSession: Session[F] =
    Session[F](F, self.driver.asyncSession())(fToT(F.makeLock))
}

/** Group all the effect specs into one big suite, which can be called for each effect. */
abstract class EffectSuite[F[_]](testkit: EffectTestkit[F]) extends Suites(
  new EffectSessionProvider(testkit) with AlgorithmSpec[F],
  new AsyncGuaranteeSpec(testkit),
  new EffectSessionProvider(testkit) with AsyncIntegrationSpec[F],
  new EffectSessionProvider(testkit) with BasicSessionSpec[F],
  new EffectSessionProvider(testkit) with BasicTransactionSpec[F],
  new EffectSessionProvider(testkit) with CompositeTypesSpec[F],
  new EffectSessionProvider(testkit) with ConcurrentSessionSpec[F],
  new EffectSessionProvider(testkit) with ParameterSpec[F],
  new EffectSessionProvider(testkit) with PathSessionSpec[F],
  new EffectSessionProvider(testkit) with QueryExecutionSpec[F],
  new StageSyntaxSpec(testkit),
  new EffectSessionProvider(testkit) with TransactIntegrationSpec[F]
)
