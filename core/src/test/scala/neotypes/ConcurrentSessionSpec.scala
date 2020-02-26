package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.cypher._
import neotypes.internal.syntax.async._
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the concurrent use of a Session[F] */
abstract class ConcurrentSessionSpec[F[_]] (implicit ct: ClassTag[F[_]]) extends BaseIntegrationSpec[F] {
  private val effectName: String = ct.runtimeClass.getCanonicalName
  behavior of s"Concurrent use of Session[${effectName}]"

  def fToFuture[T](f: F[T]): Future[T]
  def runConcurrently(a: F[Unit], b: F[Unit]): F[Unit]
  implicit def F: Async[F]

  private final def executeAsFuture[T](work: Session[F] => F[T]): Future[T] =
    fToFuture(execute(work))

  it should "throw a client exception when session used concurrently" in {
    recoverToSucceededIf[ClientException] {
      executeAsFuture { s =>
        def query(name: String): DeferredQuery[Unit] =
          c"CREATE (p: PERSON { name: ${name} })".query[Unit]

        runConcurrently(
          query(name = "name1").execute(s),
          query(name = "name2").execute(s)
        )
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.EMPTY_INIT_QUERY
}
