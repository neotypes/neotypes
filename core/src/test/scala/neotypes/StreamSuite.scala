package neotypes

import org.scalatest.Suites
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/** Testkit used to write specs abstracted from any concrete stream. */
abstract class StreamTestkit[S[_], F[_]](val effectTestkit: EffectTestkit[F])
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
abstract class BaseStreamSpec[S[_], F[_]](streamTestkit: StreamTestkit[S, F]) extends BaseEffectSpec[F](streamTestkit.effectTestkit) { self =>
  protected final val streamName: String =
    streamTestkit.streamName

  private final val behaviour: streamTestkit.Behaviour =
    streamTestkit.createBehaviour(self.executionContext)

  protected final def streamToFList[A](stream: S[A]): F[List[A]] =
    behaviour.streamToFList(stream)

  protected implicit final val S: Stream.Aux[S, F] =
    behaviour.streamInstance

  protected final def streamConcurrently[A](stream1: S[Unit], stream2: S[Unit]): S[Unit] =
    behaviour.streamConcurrently(stream1, stream2)
}

/** Provides an StreamingDriver[S, F] instance for streaming tests. */
abstract class StreamingDriverProvider[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseStreamSpec[S, F](testkit) with DriverProvider[F] { self: BaseIntegrationSpec[F] =>
  override type DriverType = StreamingDriver[S, F]

  override protected final lazy val driver: DriverType =
    Driver[S, F](self.neoDriver)

  protected final def executeAsFuture[A](work: DriverType => F[A]): Future[A] =
    fToFuture(work(driver))

  protected final def executeAsFutureList[A](work: DriverType => S[A]): Future[List[A]] =
    executeAsFuture(work andThen streamToFList)

  protected final def debugMetrics(): F[Unit] =
    F.map(driver.metrics) { metrics =>
      println(s"METRICS: ${metrics}")
    }
}

/** Group all the stream specs into one big suite, which can be called for each stream. */
abstract class StreamSuite[S[_], F[_]](testkit: StreamTestkit[S, F]) extends Suites(
  new ConcurrentStreamingDriverSpec(testkit),
  new StreamGuaranteeSpec(testkit),
  new StreamSpec(testkit),
  new StreamingDriverSpec(testkit),
  new StreamingTransactSpec(testkit),
  new StreamingTransactionSpec(testkit)
)
