package neotypes

import org.scalatest.Suites

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete stream. */
abstract class StreamTestkit[S[_], F[_]](val asyncTestkit: AsyncTestkit[F])
                                        (implicit ctS: ClassTag[S[_]]) {
  final val streamName: String = ctS.runtimeClass.getCanonicalName

  trait Behaviour {
    def streamToFList[A](stream: S[A]): F[List[A]]
    def streamInstance: Stream.Aux[S, F]
    def streamConcurrently(stream1: S[Unit], stream2: S[Unit]): S[Unit]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing stream specs. */
abstract class BaseStreamSpec[S[_], F[_]](streamTestkit: StreamTestkit[S, F]) extends BaseAsyncSpec[F](streamTestkit.asyncTestkit) {
  protected final val streamName: String =
    streamTestkit.streamName

  private final val behaviour: streamTestkit.Behaviour =
    streamTestkit.createBehaviour(this.executionContext)

  protected final def streamToFList[A](stream: S[A]): F[List[A]] =
    behaviour.streamToFList(stream)

  protected implicit final val S: Stream.Aux[S, F] =
    behaviour.streamInstance

  protected final def streamConcurrently[A](stream1: S[Unit], stream2: S[Unit]): S[Unit] =
    behaviour.streamConcurrently(stream1, stream2)
}

/** Provides an StreamDriver[S, F] instance for stream tests. */
abstract class StreamDriverProvider[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseStreamSpec[S, F](testkit) with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
  override protected final type DriverType = StreamDriver[S, F]
  override protected final type TransactionType = StreamTransaction[S, F]

  override protected final lazy val driverName: String = s"StreamDriver[${streamName}, ${asyncName}]"
  override protected final lazy val transactionName: String = s"StreamTransaction[${streamName}, ${asyncName}]"

  override protected final lazy val driver: DriverType =
    Driver.stream(self.neoDriver)

  override protected final def transaction[T](driver: DriverType): F[TransactionType] =
    F.map(S.single(driver.streamTransaction))(_.get)

  override protected final def transact[T](driver: DriverType)(txF: TransactionType => F[T]): F[T] =
    F.map(streamToFList(driver.streamTransact(txF andThen S.fromF)))(_.head)

  protected final def executeAsFutureList[A](work: DriverType => S[A]): Future[List[A]] =
    executeAsFuture(work andThen streamToFList)
}

/** Group all the stream specs into one big suite, which can be called for each stream. */
abstract class StreamSuite[S[_], F[_]](testkit: StreamTestkit[S, F]) extends Suites(
  new StreamGuaranteeSpec(testkit),
  new StreamDriverSpec(testkit),
  new StreamTransactionSpec(testkit),
  new StreamDriverTransactSpec(testkit),
  new StreamDriverConcurrentUsageSpec(testkit),
  new StreamParameterSpec(testkit),
  new StreamAlgorithmSpec(testkit)
)
