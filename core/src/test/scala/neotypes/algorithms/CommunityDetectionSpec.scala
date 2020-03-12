package neotypes.algorithms

import neotypes.BaseAlgorithmIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Matchers

class CommunityDetectionSpec extends BaseAlgorithmIntegrationSpec with Matchers{
  case class TriangleCount(nodeCount: Int, triangleCount: Int, averageClusteringCoefficient: Double)

  override def initQuery =
    """
      |MERGE (alice:Person{id:"Alice"})
      |MERGE (michael:Person{id:"Michael"})
      |MERGE (karin:Person{id:"Karin"})
      |MERGE (chris:Person{id:"Chris"})
      |MERGE (will:Person{id:"Will"})
      |MERGE (mark:Person{id:"Mark"})
      |
      |MERGE (michael)-[:KNOWS]->(karin)
      |MERGE (michael)-[:KNOWS]->(chris)
      |MERGE (will)-[:KNOWS]->(michael)
      |MERGE (mark)-[:KNOWS]->(michael)
      |MERGE (mark)-[:KNOWS]->(will)
      |MERGE (alice)-[:KNOWS]->(michael)
      |MERGE (will)-[:KNOWS]->(chris)
      |MERGE (chris)-[:KNOWS]->(karin);
    """.stripMargin

  it should "execute the triangle count algorithm" in execute { s =>
    """CALL algo.triangleCount('Person', 'KNOWS',
      {concurrency:4, write:true, writeProperty:'triangles',clusteringCoefficientProperty:'coefficient'})
      YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient;""".query[TriangleCount].single(s)
      .map(_ shouldBe TriangleCount(6, 3, 0.6055555555555555))
  }
}