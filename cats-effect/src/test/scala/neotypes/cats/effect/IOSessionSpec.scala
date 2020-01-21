package neotypes.cats.effect

import cats.effect.IO
import cats.implicits._
import neotypes.TransactIntegrationSpec
import neotypes.cats.effect.implicits._
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IOSessionSpec extends TransactIntegrationSpec[IO] {

  override def fToFuture[T](io: IO[T]): Future[T] = io.unsafeToFuture()

  it should "throw a client exception when session used in parallel" in
    testSession[org.neo4j.driver.v1.exceptions.ClientException] { s =>
      implicit val contextShift = IO.contextShift(global)

       val res = for(i <- 0 to 10) yield "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(s)

      (res(0), res(1), res(2), res(3), res(4), res(5), res(6), res(7), res(8), res(9)).parMapN{ (_, _, _, _, _, _, _, _, _, _) => ()}
    }
}
