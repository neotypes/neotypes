package neotypes

import org.neo4j.{driver => neo4j}
import org.scalatest.{AsyncTestSuite, Suites}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete stream. */
abstract class StreamTestkit[S[_], F[_]](val effectTestkit: EffectTestkit[F])(implicit ctS: ClassTag[S[_]]) {
  final val streamName: String = ctS.runtimeClass.getCanonicalName

  trait Behaviour {
    def streamToFList[T](stream: S[T]): F[List[T]]
    def streamInstance: Stream.Aux[S, F]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing stream specs. */
abstract class BaseStreamSpec[S[_], F[_]] (protected val streamTestkit: StreamTestkit[S, F]) extends BaseEffectSpec(streamTestkit.effectTestkit) with AsyncTestSuite {
  protected final val streamName: String =
    streamTestkit.streamName

  protected final val streamBehaviour: streamTestkit.Behaviour =
    streamTestkit.createBehaviour(this.executionContext)

  protected final def streamToFList[T](stream: S[T]): F[List[T]] =
    streamBehaviour.streamToFList(stream)

  protected implicit final val S: Stream.Aux[S, F] =
    streamBehaviour.streamInstance
}

/** Provides an reactive session for streaming tests. */
abstract class StreamSessionProvider[S[_], F[_]] (testkit: StreamTestkit[S, F]) extends BaseStreamSpec[S, F](testkit) with SessionProvider[F] { self: BaseIntegrationSpec[F] =>
  override protected final val sessionType: String = "RxSession"

  override protected final lazy val neotypesSession: StreamingSession[S, F] =
    Session[S, F](F, S, this.driver.rxSession())(fToT(F.makeLock))

  protected final def executeAsFutureList[T](work: StreamingSession[S, F] => S[T]): Future[List[T]] =
    executeAsFuture(work andThen streamToFList)
}

/** Provides an asynchronous session for streaming tests. */
abstract class LegacyStreamSessionProvider[S[_], F[_]] (testkit: StreamTestkit[S, F]) extends BaseStreamSpec[S, F](testkit) with SessionProvider[F] { self: BaseIntegrationSpec[F] =>
  override protected final val sessionType: String = "AsyncSession"

  override protected final lazy val neotypesSession: Session[F] =
    Session[F](F,this.driver.asyncSession())(fToT(F.makeLock))

  protected final def executeAsFutureList[T](work: Session[F] => S[T]): Future[List[T]] =
    executeAsFuture(work andThen streamToFList)
}
/** Group all the stream specs into one big suite, which can be called for each stream. */
abstract class StreamSuite[S[_], F[_]](testkit: StreamTestkit[S, F]) extends Suites(
  new LegacyStreamSessionProvider(testkit) with LegacyStreamIntegrationSpec[S, F],
  new StreamSessionProvider(testkit) with NewStreamIntegrationSpec[S, F],
  new StreamSessionProvider(testkit) with AlgorithmSpec[F],
  new StreamSessionProvider(testkit) with AsyncIntegrationSpec[F],
  new StreamSessionProvider(testkit) with BasicSessionSpec[F],
  new StreamSessionProvider(testkit) with BasicTransactionSpec[F],
  new StreamSessionProvider(testkit) with CompositeTypesSpec[F],
  new StreamSessionProvider(testkit) with ConcurrentSessionSpec[F],
  new StreamSessionProvider(testkit) with ParameterSpec[F],
  new StreamSessionProvider(testkit) with PathSessionSpec[F],
  new StreamSessionProvider(testkit) with QueryExecutionSpec[F],
  new StreamSessionProvider(testkit) with TransactIntegrationSpec[F]
)
