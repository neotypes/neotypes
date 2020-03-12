package neotypes.algorithms

import neotypes.BaseAlgorithmIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Matchers

class LinkPredictionSpec extends BaseAlgorithmIntegrationSpec with Matchers{
  case class AdamicLink(score: Double)

  override def initQuery =
    """MERGE (zhen:Person {name: "Zhen"})
      |MERGE (praveena:Person {name: "Praveena"})
      |MERGE (michael:Person {name: "Michael"})
      |MERGE (arya:Person {name: "Arya"})
      |MERGE (karin:Person {name: "Karin"})
      |
      |MERGE (zhen)-[:FRIENDS]-(arya)
      |MERGE (zhen)-[:FRIENDS]-(praveena)
      |MERGE (praveena)-[:WORKS_WITH]-(karin)
      |MERGE (praveena)-[:FRIENDS]-(michael)
      |MERGE (michael)-[:WORKS_WITH]-(karin)
      |MERGE (arya)-[:FRIENDS]-(karin)""".stripMargin

  it should "execute the Adamic Adar algorithm" in execute { s =>
    """MATCH (p1:Person {name: 'Michael'})
      MATCH (p2:Person {name: 'Karin'})
      RETURN algo.linkprediction.adamicAdar(p1, p2) AS score""".query[AdamicLink].single(s)
      .map( _ shouldBe AdamicLink(0.9102392266268373))
  }
}