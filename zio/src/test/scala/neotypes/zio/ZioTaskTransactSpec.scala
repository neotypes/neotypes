package neotypes.cats.effect

import zio.{DefaultRuntime, Task}
import zio.internal.PlatformLive
import neotypes.TransactIntegrationSpec
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.zio.implicits._
import org.neo4j.driver.v1.exceptions.ClientException

import scala.concurrent.{ExecutionContext, Future}

class ZioTaskTransactSpec extends TransactIntegrationSpec[Task] { self =>
  import TransactIntegrationSpec.CustomException

  val runtime = new DefaultRuntime { override val platform = PlatformLive.fromExecutionContext(self.executionContext) }

  override def fToFuture[T](task: Task[T]): Future[T] = runtime.unsafeRunToFuture(task)

  it should "execute & commit multiple queries inside the same transact" in
    ensureCommitedTransaction(expectedResults = List("Luis", "Dmitry")) { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
        r <- "MATCH (p: PERSON) RETURN p.name".query[String].list(tx)
      } yield r
    }

  it should "automatically rollback if any query fails inside a transact" in
    ensureRollbackedTransaction[ClientException] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- "broken cypher query".query[Unit].execute(tx)
      } yield ()
    }

  it should "automatically rollback if there is an error inside the transact" in
    ensureRollbackedTransaction[CustomException] { tx =>
      for {
        _ <- "CREATE (p: PERSON { name: \"Luis\" })".query[Unit].execute(tx)
        _ <- Task.fail(CustomException)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()
    }
}
