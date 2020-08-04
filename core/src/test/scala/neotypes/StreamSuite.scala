package neotypes

import org.scalatest.Suites
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
abstract class BaseStreamSpec[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseIntegrationSpec[F](testkit.effectTestkit) { self =>
  protected final val streamName: String =
    testkit.streamName

  private final val behaviour: testkit.Behaviour =
    testkit.createBehaviour(self.executionContext)

  override protected final lazy val neotypesSession =
    Session[S, F](F, S, driver.rxSession())(fToT(F.makeLock))

  protected final def executeAsFutureList[T](work: StreamingSession[S, F] => S[T]): Future[List[T]] =
    fToFuture(behaviour.streamToFList(work(neotypesSession)))

  protected implicit final val S: Stream.Aux[S, F] =
    behaviour.streamInstance
}

/** Group all the stream specs into one big suite, which can be called for each stream. */
abstract class StreamSuite[S[_], F[_]](testkit: StreamTestkit[S, F]) extends Suites(
  new OldStreamIntegrationSpec(testkit),
  new NewStreamIntegrationSpec(testkit),
  new EffectSuite[F](testkit.effectTestkit) {}
)
