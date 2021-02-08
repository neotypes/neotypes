package neotypes.cats.effect

import neotypes.{AsyncDriverProvider, BaseIntegrationSpec, Driver}
import neotypes.cats.effect.implicits._
import neotypes.implicits.all._

import cats.{Applicative, Monad}
import cats.effect.{Async, IO, Resource}
import cats.syntax.all._

/** Ensures the neotypes implicits does not collide with the cats ones. */
final class CatsImplicitsSpec extends AsyncDriverProvider(IOTestkit) with BaseIntegrationSpec[IO] { self =>
  it should "work with cats implicits and neotypes implicits" in {
    def test1[F[_] : Applicative]: F[Unit] = Applicative[F].unit
    def test2[F[_] : Monad]: F[Unit] = ().pure[F]

    def makeDriver[F[_] : Async]: Resource[F, Driver[F]] =
      Resource.make(Async[F].delay(Driver[F](this.neoDriver)))(_.close)

    def useDriver[F[_] : Async]: F[String] = makeDriver[F].use { d =>
      (test1[F] *> test2[F]).flatMap { _ =>
        "MATCH (p: Person { name: \"Charlize Theron\" }) RETURN p.name"
          .query[String]
          .single(d)
      }
    }

    val test = useDriver[IO].map { name =>
      assert(name == "Charlize Theron")
    }

    fToFuture(test)
  }

  override final val initQuery: String = BaseIntegrationSpec.DEFAULT_INIT_QUERY
}
