package neotypes.algorithms

import neotypes.BaseAlgorithmIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Matchers

class CentralitySpec extends BaseAlgorithmIntegrationSpec with Matchers {
  case class ArticleRanking(nodes: Int, iterations: Int, dampingFactor: Double, write: Boolean, writeProperty: String)

  override def initQuery =
    """
      |MERGE (paper0:Paper {name:'Paper 0'})
      |MERGE (paper1:Paper {name:'Paper 1'})
      |MERGE (paper2:Paper {name:'Paper 2'})
      |MERGE (paper3:Paper {name:'Paper 3'})
      |MERGE (paper4:Paper {name:'Paper 4'})
      |MERGE (paper5:Paper {name:'Paper 5'})
      |MERGE (paper6:Paper {name:'Paper 6'})
      |
      |MERGE (paper1)-[:CITES]->(paper0)
      |
      |MERGE (paper2)-[:CITES]->(paper0)
      |MERGE (paper2)-[:CITES]->(paper1)
      |
      |MERGE (paper3)-[:CITES]->(paper0)
      |MERGE (paper3)-[:CITES]->(paper1)
      |MERGE (paper3)-[:CITES]->(paper2)
      |
      |MERGE (paper4)-[:CITES]->(paper0)
      |MERGE (paper4)-[:CITES]->(paper1)
      |MERGE (paper4)-[:CITES]->(paper2)
      |MERGE (paper4)-[:CITES]->(paper3)
      |
      |MERGE (paper5)-[:CITES]->(paper1)
      |MERGE (paper5)-[:CITES]->(paper4)
      |
      |MERGE (paper6)-[:CITES]->(paper1)
      |MERGE (paper6)-[:CITES]->(paper4);
    """.stripMargin

  it should "execute the article rank algorithm" in execute { s =>
    """CALL algo.articleRank('Paper', 'CITES',
      {iterations:20, dampingFactor:0.85, write: true,writeProperty:"pagerank"})
      YIELD nodes, iterations, dampingFactor, write, writeProperty""".query[ArticleRanking].single(s)
      .map(_ shouldBe ArticleRanking(7, 20, 0.85, true, "pagerank"))
  }
}