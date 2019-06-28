package neotypes.cats

import cats.Applicative
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import neotypes.cats.implicits._
import neotypes.Async._
import neotypes.implicits._
import neotypes.implicits.all._
import neotypes.{BaseIntegrationSpec, Session}
import org.neo4j.driver.v1.exceptions.ClientException
import org.scalatest.AsyncFlatSpec
import cats.Monad

class CatsImplicitsSpec extends AsyncFlatSpec with BaseIntegrationSpec {

  it should "work with cats implicits and neotypes implicits" in {
    def test1[F[_]: Applicative]: F[Unit] = Applicative[F].unit

    def test2[F[_]: Monad]: F[Unit] = ().pure[F]

    def makeSession[F[_]: Async]: F[Session[F]] =
      (test1[F] *> test2[F]).flatMap(_ => driver.session().asScala[F].pure[F])

    val s = makeSession[IO].unsafeRunSync()

    """match (p:Person {name: "Charlize Theron"}) return p.name"""
      .query[String]
      .single(s)
      .unsafeToFuture()
      .map {
        name => assert(name == "Charlize Theron")
      }

    recoverToSucceededIf[ClientException] {
      "match test return p.name"
        .query[String]
        .single(s)
        .unsafeToFuture()
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
