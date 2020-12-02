package neotypes.akkastreams

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.dimafeng.testcontainers.{ForAllTestContainer, Neo4jContainer}
import org.neo4j.{driver => neo4j}
import org.reactivestreams.Publisher
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.testcontainers.utility.DockerImageName

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

final class ReproduceError extends AsyncFlatSpec with ForAllTestContainer {
  override final val container =
    Neo4jContainer(neo4jImageVersion = DockerImageName.parse("neo4j:latest")).configure(_.withoutAuthentication())

  private final lazy val driver =
    neo4j.GraphDatabase.driver(container.boltUrl)

  private final lazy val neotypesSession =
    new NeotypesSession(this.driver.rxSession)

  override final def afterStart(): Unit = {
    // Force evaluation of the driver and the session.
    neotypes.internal.utils.void(this.neotypesSession)
  }

  override final def beforeStop(): Unit = {
    driver.close()
  }

  it should "work" in {
    def loop(attempts: Int): Future[Assertion] = {
      println()
      println("--------------------------------------------------")
      println(s"Remaining attempts ${attempts}")

      neotypesSession.run("MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name").flatMap { r =>
        println(s"Results: ${r}")
        if (attempts > 0) loop(attempts - 1)
        else Future.successful(succeed)
      }
    }

    neotypesSession.run("CREATE (Charlize: Person { name: 'Charlize Theron', born: 1975 })").flatMap { _ =>
      loop(attempts = 1000)
    } recover {
      case NoTransactionError => fail(NoTransactionError.getMessage)
    }
  }
}

final class NeotypesSession (session: neo4j.reactive.RxSession)(implicit ec: ExecutionContext) {
  implicit private val system = ActorSystem(name = "QuickStart")

  import Syntax._

  def run(query: String): Future[Option[Map[String, String]]] = {
    def runQuery(tx: neo4j.reactive.RxTransaction): Future[Option[Map[String, String]]] =
      tx
        .run(query)
        .records
        .toStream
        .map { record =>
          record
            .fields
            .asScala
            .iterator
            .map(p => p.key -> p.value.toString)
            .toMap
        }.single

    for {
      tx <- session.beginTransaction.toStream.single.transform(_.flatMap(_.toRight(left = NoTransactionError).toTry))
      result <- runQuery(tx)
      _ <- tx.commit[Unit].toStream.void
    } yield result
  }
}

object Syntax {
  implicit final class PublisherOps[A] (private val publisher: Publisher[A]) extends AnyVal {
    def toStream: AkkaStream[A] =
      Source.fromPublisher(publisher)
  }

  implicit final class StreamOps[A] (private val sa: AkkaStream[A]) extends AnyVal {
    def list(implicit mat: Materializer): Future[Seq[A]] =
      sa.runWith(Sink.seq)

    def single(implicit mat: Materializer): Future[Option[A]] =
      sa.take(1).runWith(Sink.lastOption)

    def void(implicit mat: Materializer, ec: ExecutionContext): Future[Unit] =
      sa.runWith(Sink.ignore).map(_ => ())
  }
}

object NoTransactionError extends Throwable("Transaction was not created!") with NoStackTrace
