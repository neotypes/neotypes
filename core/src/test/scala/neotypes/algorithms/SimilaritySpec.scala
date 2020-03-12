package neotypes.algorithms

import neotypes.BaseAlgorithmIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Matchers

class SimilaritySpec extends BaseAlgorithmIntegrationSpec with Matchers{
  case class JaccardSimilarity(from: String, to: String, similarity: Double)

  override def initQuery =
    """MERGE (french:Cuisine {name:'French'})
      |MERGE (italian:Cuisine {name:'Italian'})
      |MERGE (indian:Cuisine {name:'Indian'})
      |MERGE (lebanese:Cuisine {name:'Lebanese'})
      |MERGE (portuguese:Cuisine {name:'Portuguese'})
      |
      |MERGE (zhen:Person {name: "Zhen"})
      |MERGE (praveena:Person {name: "Praveena"})
      |MERGE (michael:Person {name: "Michael"})
      |MERGE (arya:Person {name: "Arya"})
      |MERGE (karin:Person {name: "Karin"})
      |
      |MERGE (praveena)-[:LIKES]->(indian)
      |MERGE (praveena)-[:LIKES]->(portuguese)
      |
      |MERGE (zhen)-[:LIKES]->(french)
      |MERGE (zhen)-[:LIKES]->(indian)
      |
      |MERGE (michael)-[:LIKES]->(french)
      |MERGE (michael)-[:LIKES]->(italian)
      |MERGE (michael)-[:LIKES]->(indian)
      |
      |MERGE (arya)-[:LIKES]->(lebanese)
      |MERGE (arya)-[:LIKES]->(italian)
      |MERGE (arya)-[:LIKES]->(portuguese)
      |
      |MERGE (karin)-[:LIKES]->(lebanese)
      |MERGE (karin)-[:LIKES]->(italian)""".stripMargin

  it should "execute the jaccard similarity algorithm" in execute { s =>
    """MATCH (p1:Person {name: 'Karin'})-[:LIKES]->(cuisine1)
      WITH p1, collect(id(cuisine1)) AS p1Cuisine
      MATCH (p2:Person {name: "Arya"})-[:LIKES]->(cuisine2)
      WITH p1, p1Cuisine, p2, collect(id(cuisine2)) AS p2Cuisine
      RETURN p1.name AS from,
             p2.name AS to,
             algo.similarity.jaccard(p1Cuisine, p2Cuisine) AS similarity""".query[JaccardSimilarity].single(s)
      .map(_ shouldBe JaccardSimilarity("Karin", "Arya", 0.6666666666666666))
  }
}