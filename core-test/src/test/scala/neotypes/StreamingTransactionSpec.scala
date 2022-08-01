package neotypes

import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import neotypes.internal.syntax.stream._

import org.scalatest.matchers.should.Matchers

/** Base class for testing the basic behaviour of StreamingTransaction[S, F] instances. */
final class StreamingTransactionSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamingDriverProvider[S, F](testkit) with CleaningIntegrationSpec[F] with Matchers {
  behavior of s"StreamingTransaction[${streamName}, ${effectName}]"

  it should "explicitly commit a transaction" in {
    executeAsFutureList { d =>
      d.streamingTransaction.evalMap { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
          _ <- tx.commit
        } yield ()
      } flatMapS { _ =>
        "MATCH (p: PERSON) RETURN p.name".query[String].stream(d)
      }
    } map { people =>
      people should contain theSameElementsAs List("Luis", "Dmitry")
    }
  }

  it should "explicitly rollback a transaction" in {
    executeAsFutureList { d =>
      d.streamingTransaction.evalMap { tx =>
        for {
          _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
          _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
          _ <- tx.rollback
        } yield ()
      } flatMapS { _ =>
        "MATCH (p: PERSON) RETURN p.name".query[String].stream(d)
      }
    } map { people =>
      people shouldBe empty
    }
  }
}
