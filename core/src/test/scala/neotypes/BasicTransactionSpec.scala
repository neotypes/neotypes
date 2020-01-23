package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._

import scala.concurrent.Future

class BasicTransactionSpec extends CleaningIntegrationSpec[Future] {
  it should "create and commit data" in {
    execute {
      tx => for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()
    }.flatMap{
      _ => execute{ tx2 =>
        "MATCH (p: PERSON) RETURN p.name".query[String].list(tx2)
      } map { people =>
        assert(people == List("Luis", "Dmitry"))
      }
    }
  }
  it should "rollback a transaction on exception" in {
    execute { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield throw new Exception("Boom!")
    }.recoverWith{
      case _ => execute{ tx2 =>
          "MATCH (p: PERSON) RETURN p.name".query[String].list(tx2)
      } map { people =>
        assert(people == List.empty)
      }
    }
  }
}
