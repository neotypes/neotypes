package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.junit.runner.RunWith
import org.neo4j.driver.v1.GraphDatabase
import org.scalatest.AsyncFlatSpec
import org.scalatest.junit.JUnitRunner
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

@RunWith(classOf[JUnitRunner])
abstract class BaseIntegrationSpec(initQuery: String) extends AsyncFlatSpec with ForAllTestContainer {
  override val container = GenericContainer("neo4j:3.4.5",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(15))
  )

  lazy val driver = GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  override def afterStart(): Unit = {
    val session = driver.session()
    try {
      session.writeTransaction(tx =>
        tx.run(initQuery)
      )
    } finally {
      session.close()
    }
  }
}