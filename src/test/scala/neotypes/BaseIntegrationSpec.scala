package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.neo4j.driver.v1
import org.neo4j.driver.v1.{GraphDatabase, TransactionWork}
import org.scalatest.Suite
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

trait BaseIntegrationSpec extends Suite with ForAllTestContainer {
  val initQuery: String = null

  override val container = GenericContainer("neo4j:3.4.5",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(15))
  )

  lazy val driver = GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  override def afterStart(): Unit = {
    if (initQuery != null) {
      val session = driver.session()
      try {
        session.writeTransaction(new TransactionWork[Unit] {
          override def execute(tx: v1.Transaction): Unit = tx.run(initQuery)
        })
      } finally {
        session.close()
      }
    }
  }
}