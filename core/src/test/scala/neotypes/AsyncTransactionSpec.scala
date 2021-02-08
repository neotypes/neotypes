package neotypes

import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._

/** Base class for testing the basic behaviour of Transaction[F] instances. */
final class AsyncTransactionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with CleaningIntegrationSpec[F] {
  behavior of s"Transaction[${effectName}]"

  it should "explicitly commit a transaction" in executeAsFuture { d =>
    for {
      tx <- d.transaction
      _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
      _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      _ <- tx.commit
      people <- "MATCH (p: PERSON) RETURN p.name".query[String].set(d)
    } yield  {
      assert(people == Set("Luis", "Dmitry"))
    }
  }

  it should "explicitly rollback a transaction" in executeAsFuture { d =>
    for {
      tx <-d.transaction
      _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
      _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      _ <- tx.rollback
      people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(d)
    } yield {
      assert(people.isEmpty)
    }
  }
}
