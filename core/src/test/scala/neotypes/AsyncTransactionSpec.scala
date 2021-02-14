package neotypes

import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.scalatest.matchers.should.Matchers._


/** Base class for testing the basic behaviour of Transaction[F] instances. */
final class AsyncTransactionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with CleaningIntegrationSpec[F] {
  s"Transaction[${effectName}]" should {
    "explicitly commit a transaction" in executeAsFuture { d =>
      for {
        tx <- d.transaction
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.commit
        people <- "MATCH (p: PERSON) RETURN p.name".query[String].set(d)
      } yield  {
        people shouldBe Set("Luis", "Dmitry")
      }
    }
    "explicitly rollback a transaction" in executeAsFuture { d =>
      for {
        tx <-d.transaction
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.rollback
        people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(d)
      } yield {
        people shouldBe Nil
      }
    }
  }
}
