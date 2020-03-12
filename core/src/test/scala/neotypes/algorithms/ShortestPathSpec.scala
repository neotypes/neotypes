package neotypes.algorithms

import neotypes.BaseAlgorithmIntegrationSpec
import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import org.scalatest.Matchers

class ShortestPathSpec extends BaseAlgorithmIntegrationSpec with Matchers {
  final case class ShortestPath(nodeCount: Int, totalCost: Double)

  override def initQuery =
    """
      |MERGE (a:Loc {name:'A'})
      |MERGE (b:Loc {name:'B'})
      |MERGE (c:Loc {name:'C'})
      |MERGE (d:Loc {name:'D'})
      |MERGE (e:Loc {name:'E'})
      |MERGE (f:Loc {name:'F'})
      |
      |MERGE (a)-[:ROAD {cost:50}]->(b)
      |MERGE (a)-[:ROAD {cost:50}]->(c)
      |MERGE (a)-[:ROAD {cost:100}]->(d)
      |MERGE (b)-[:ROAD {cost:40}]->(d)
      |MERGE (c)-[:ROAD {cost:40}]->(d)
      |MERGE (c)-[:ROAD {cost:80}]->(e)
      |MERGE (d)-[:ROAD {cost:30}]->(e)
      |MERGE (d)-[:ROAD {cost:80}]->(f)
      |MERGE (e)-[:ROAD {cost:40}]->(f);
    """.stripMargin

  it should "execute the shortest path algorithm" in execute { s =>
    """MATCH (start:Loc{name:'A'}), (end:Loc{name:'F'})
                CALL algo.shortestPath(start, end, 'cost',{write:true,writeProperty:'sssp'})
                YIELD writeMillis,loadMillis,nodeCount, totalCost
                RETURN writeMillis,loadMillis,nodeCount,totalCost""".query[ShortestPath].single(s)
      .map(_ shouldBe ShortestPath(5, 160.0))
  }
}