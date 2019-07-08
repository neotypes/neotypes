package neotypes.cats

import cats.{Applicative, Monad}
import cats.effect.{Async, IO, Resource}
import cats.effect.implicits._
import cats.implicits._
import neotypes.{BaseIntegrationSpec, Driver, Session}
import neotypes.cats.effect.implicits._
import neotypes.implicits.all._
import org.neo4j.driver.v1.exceptions.ClientException

/** Ensures the neotypes implicits does not collide with the cats ones. */
class CatsImplicitsSpec extends BaseIntegrationSpec[IO] {
  it should "work with cats implicits and neotypes implicits" in {
    def test1[F[_]: Applicative]: F[Unit] = Applicative[F].unit
    def test2[F[_]: Monad]: F[Unit] = ().pure[F]

    def makeSession[F[_]: Async]: Resource[F, Session[F]] =
      Resource
        .make(Async[F].delay(new Driver[F](driver)))(_.close)
        .flatMap(_.session)

    def useSession[F[_]: Async] = makeSession[F].use { s =>
      (test1[F] *> test2[F]).flatMap { _=>
        """match (p:Person {name: "Charlize Theron"}) return p.name"""
          .query[String]
          .single(s)
      }
    }

    useSession[IO].unsafeToFuture().map {
      name => assert(name == "Charlize Theron")
    }
  }

  override val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
