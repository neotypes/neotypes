package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.neo4j.driver.v1
import org.neo4j.driver.v1.{GraphDatabase, TransactionWork}
import org.scalatest.Suite
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

trait BaseIntegrationSpec extends Suite with ForAllTestContainer {
  def initQuery: String

  override val container = GenericContainer("neo4j:3.5.3",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(30))
  )

  lazy val driver = GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  override def afterStart(): Unit = {
    if (initQuery != null) {
      val session = driver.session()
      try {
        session.writeTransaction(
          new TransactionWork[Unit] {
            override def execute(tx: v1.Transaction): Unit = tx.run(initQuery)
          }
        )
      } finally {
        session.close()
      }
    }
  }
}

object BaseIntegrationSpec {
  final val DEFAULT_INIT_QUERY: String =
    """CREATE (Charlize:Person {name:'Charlize Theron', born:1975})
      |CREATE (ThatThingYouDo:Movie {title:'That Thing You Do', released:1996, tagline:'In every life there comes a time when that thing you dream becomes that thing you do'})
      |CREATE (Charlize)-[:ACTED_IN {roles:['Tina']}]->(ThatThingYouDo)
      |CREATE (t:Test {added: date('2018-11-26')})
      |CREATE (ThatThingYouDo)-[:TEST_EDGE]->(t)""".stripMargin
}
