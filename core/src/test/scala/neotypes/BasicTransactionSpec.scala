package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers._


/** Base class for testing the basic behaviour of Transaction[F] instances. */
final class BasicTransactionSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationWordSpec(testkit) {
  s"Transaction[${effectName}]" should {
    "explicitly commit a transaction" in executeAsFuture { s =>
      for {
        tx <- s.transaction
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.commit
        people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
      } yield  {
        people shouldBe List("Luis", "Dmitry")
      }
    }

    "explicitly rollback a transaction" in executeAsFuture { s =>
      for {
        tx <-s.transaction
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.rollback
        people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
      } yield {
        people shouldBe List.empty
      }
    }
  }
}
