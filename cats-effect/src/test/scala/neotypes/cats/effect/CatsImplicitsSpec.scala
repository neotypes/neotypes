package neotypes.cats.effect

import cats.{Applicative, Monad}
import cats.effect.{Concurrent, ContextShift, IO, Resource}
import cats.syntax.all._
import neotypes.{BaseIntegrationSpec, Driver, Session}
import neotypes.cats.effect.implicits._
import neotypes.implicits.all._
import org.neo4j.driver.exceptions.ClientException
import org.testcontainers.shaded.okio.AsyncTimeout

/** Ensures the neotypes implicits does not collide with the cats ones. */
final class CatsImplicitsSpec extends BaseIntegrationSpec[IO](IOTestkit) { self =>
  it should "work with cats implicits and neotypes implicits" in {
    def test1[F[_]: Applicative]: F[Unit] = Applicative[F].unit
    def test2[F[_]: Monad]: F[Unit] = ().pure[F]

    def makeSession[F[_]: Concurrent]: Resource[F, Session[F]] =
      Resource
        .make(Concurrent[F].delay(new Driver[F](this.driver)))(_.close)
        .flatMap(_.session)

    def useSession[F[_]: Concurrent]: F[String] = makeSession[F].use { s =>
      (test1[F] *> test2[F]).flatMap { _ =>
        """match (p:Person {name: "Charlize Theron"}) return p.name"""
          .query[String]
          .single(s)
      }
    }

    implicit val cs: ContextShift[IO] = IO.contextShift(self.executionContext)
    useSession[IO].unsafeToFuture().map { name =>
      assert(name == "Charlize Theron")
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
