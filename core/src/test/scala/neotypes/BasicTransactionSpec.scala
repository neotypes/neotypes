package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._

/** Base class for testing the basic behaviour of Transaction[F] instances. */
final class BasicTransactionSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationSpec(testkit) {
  behavior of s"Transaction[${effectName}]"

  it should "explicitly commit a transaction" in executeAsFuture { s =>
    for {
      tx <- s.transaction
      _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
      _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      _ <- tx.commit
      people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
    } yield  {
      assert(people == List("Luis", "Dmitry"))
    }
  }

  it should "explicitly rollback a transaction" in executeAsFuture { s =>
    for {
      tx <-s.transaction
      _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
      _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      _ <- tx.rollback
      people <- "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
    } yield {
      assert(people == List.empty)
    }
  }
}
