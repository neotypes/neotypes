package neotypes.cats.effect

import monix.eval.Task
import monix.execution.Scheduler
import neotypes.TransactIntegrationSpec
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.monix.implicits._
import org.neo4j.driver.v1.exceptions.ClientException

import scala.concurrent.{ExecutionContext, Future}

class MonixTaskTransactSpec extends TransactIntegrationSpec[Task] { self =>
  import TransactIntegrationSpec.CustomException

  implicit val scheduler: Scheduler = Scheduler(self.executionContext)

  override def fToFuture[T](task: Task[T]): Future[T] = task.runToFuture

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
        _ <- Task.raiseError(CustomException)
        _ <- "CREATE (p: PERSON { name: \"Dmitry\" })".query[Unit].execute(tx)
      } yield ()
    }
}
