package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._

import scala.concurrent.Future

class BasicTransactionSpec extends CleaningIntegrationSpec[Future] {
  it should "explicitly commit a transaction" in execute { s =>
    s.transaction.flatMap { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.commit
      } yield ()
    } flatMap { _ =>
      "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
    } map { people =>
      assert(people == List("Luis", "Dmitry"))
    }
  }

  it should "explicitly rollback a transaction" in execute { s =>
    s.transaction.flatMap { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        _ <- tx.rollback
      } yield ()
    } flatMap { _ =>
      "MATCH (p: PERSON) RETURN p.name".query[String].list(s)
    } map { people =>
      assert(people == List.empty)
    }
  }
}
