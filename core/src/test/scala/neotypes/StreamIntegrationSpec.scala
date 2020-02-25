package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.compatible.Assertion
import scala.concurrent.Future

/** Base class for testing the basic behavoir of Stream[S, F] instances. */
abstract class StreamIntegrationSpec[S[_], F[_]] extends BaseIntegrationSpec[F] {
  def fToFuture[T](f: F[T]): Future[T]
  def streamToFList[T](stream: S[T]): F[List[T]]

  implicit def F: Async[F]
  implicit def S: Stream.Aux[S, F]

  private final def executeAsFuture[T](work: Session[F] => S[T]): Future[List[T]] =
    fToFuture(execute(work andThen streamToFList))

  it should s"execute a streaming query" in {
    executeAsFuture { s =>
      "match (p: Person) return p.name"
        .query[Int]
        .stream(s)
    } map { names =>
      assert(names == (0 to 10).toList)
    }
  }

  it should s"catch exceptions inside the stream" in {
    recoverToSucceededIf[ClientException] {
      executeAsFuture { s =>
        "match test return p.name"
          .query[String]
          .stream(s)
      }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
