package neotypes

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete Stream type. */
abstract class StreamTestkit[S[_], F[_], A](val asyncTestkit: AsyncTestkit[F])(implicit ctS: ClassTag[S[A]]) {
  final val streamName: String = ctS.runtimeClass.getCanonicalName

  trait Behaviour {
    def streamInstance: Stream.Aux[S, F]
    def streamConcurrently(stream1: S[Unit], stream2: S[Unit]): S[Unit]
  }

  def createBehaviour(implicit ec: ExecutionContext): Behaviour
}

/** Base class for writing Stream specs. */
abstract class BaseStreamSpec[S[_], F[_], A](streamTestkit: StreamTestkit[S, F, A])
    extends BaseAsyncSpec[F](streamTestkit.asyncTestkit) {
  protected final val streamName: String =
    streamTestkit.streamName

  private final val behaviour: streamTestkit.Behaviour =
    streamTestkit.createBehaviour(this.executionContext)

  protected implicit final val S: Stream.Aux[S, F] =
    behaviour.streamInstance

  protected final def streamConcurrently(stream1: S[Unit], stream2: S[Unit]): S[Unit] =
    behaviour.streamConcurrently(stream1, stream2)

  protected final def streamToFList[B](stream: S[B]): F[List[B]] =
    S.collectAs(stream)(List)
}

/** Provides an StreamDriver[S, F] instance for Stream tests. */
abstract class StreamDriverProvider[S[_], F[_], A](testkit: StreamTestkit[S, F, A])
    extends BaseStreamSpec[S, F, A](testkit)
    with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
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

  protected final def executeAsFutureList[B](work: DriverType => S[B]): Future[List[B]] =
    executeAsFuture(work andThen streamToFList)
}
