package neotypes

import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.scalatest.matchers.should.Matchers

/** Base class for testing the use of the library with the Neo4j graph-data-science plugin. */
final class AlgorithmSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationSpec(testkit) with Matchers {
  behavior of s"The Neo4j graph-data-science plugin used with: ${effectName}"

  import AlgorithmData._

  it should "execute the article rank centrality algorithm" in executeAsFuture { s =>
    for {
      _ <- articleRankingData.query[Unit].execute(s)
      result <- """CALL algo.articleRank('Paper', 'CITES',
                   {iterations:20, dampingFactor:0.85, write: true, writeProperty:"pagerank"})
                   YIELD nodes, iterations, dampingFactor, write, writeProperty
                """.query[ArticleRanking].single (s)
    } yield {
      result shouldBe ArticleRanking(
        nodes = 7,
        iterations = 20,
        dampingFactor = 0.85,
        write = true,
        writeProperty = "pagerank"
      )
    }
  }

  it should "execute the triangle count community detection algorithm" in executeAsFuture { s =>
    for{
      _ <- triangleCountData.query[Unit].execute(s)
      result <- """CALL algo.triangleCount('Person', 'KNOWS',
                   {concurrency:4, write:true, writeProperty:'triangles', clusteringCoefficientProperty:'coefficient'})
                   YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient
                """.query[TriangleCount].single(s)
    } yield {
      result shouldBe TriangleCount(
        nodeCount = 6,
        triangleCount = 3,
        0.6055555555555555
      )
    }
  }

  it should "execute the adamic adar link prediction algorithm" in executeAsFuture { s =>
    for{
      _ <- linkPredictionData.query[Unit].execute(s)
      result <- """MATCH (p1:Person {name: 'Michael'})
                   MATCH (p2:Person {name: 'Karin'})
                   RETURN algo.linkprediction.adamicAdar(p1, p2) AS score
                """.query[AdamicLink].single(s)
    } yield {
      result shouldBe AdamicLink(
        score = 0.9102392266268373
      )
    }
  }

  it should "execute the shortest path algorithm" in executeAsFuture { s =>
    for{
      _ <- shortestPathData.query[Unit].execute(s)
      result <- """MATCH (start:Loc{name:'A'}), (end:Loc{name:'F'})
                   CALL algo.shortestPath(start, end, 'cost', {write:true, writeProperty:'sssp'})
                   YIELD writeMillis, loadMillis, nodeCount, totalCost
                   RETURN writeMillis, loadMillis, nodeCount, totalCost
                """.query[ShortestPath].single(s)
    } yield {
      result shouldBe ShortestPath(
        nodeCount = 5,
        totalCost = 160.0
      )
    }
  }

  it should "execute the jaccard similarity algorithm" in executeAsFuture { s =>
    for{
      _ <- similarityData.query[Unit].execute(s)
      result <- """MATCH (p1:Person {name: 'Karin'})-[:LIKES]->(cuisine1)
                   WITH p1, collect(id(cuisine1)) AS p1Cuisine
                   MATCH (p2:Person {name: "Arya"})-[:LIKES]->(cuisine2)
                   WITH p1, p1Cuisine, p2, collect(id(cuisine2)) AS p2Cuisine
                   RETURN
                     p1.name AS from,
                     p2.name AS to,
                     algo.similarity.jaccard(p1Cuisine, p2Cuisine) AS similarity
                """.query[JaccardSimilarity].single(s)
    } yield {
      result shouldBe JaccardSimilarity(
        from = "Karin",
        to = "Arya",
        similarity = 0.6666666666666666
      )
    }
  }
}

object AlgorithmData{
  final case class ArticleRanking(nodes: Int, iterations: Int, dampingFactor: Double, write: Boolean, writeProperty: String)
  final case class TriangleCount(nodeCount: Int, triangleCount: Int, averageClusteringCoefficient: Double)
  final case class AdamicLink(score: Double)
  final case class ShortestPath(nodeCount: Int, totalCost: Double)
  final case class JaccardSimilarity(from: String, to: String, similarity: Double)

  val articleRankingData =
    """MERGE (paper0:Paper {name:'Paper 0'})
       MERGE (paper1:Paper {name:'Paper 1'})
       MERGE (paper2:Paper {name:'Paper 2'})
       MERGE (paper3:Paper {name:'Paper 3'})
       MERGE (paper4:Paper {name:'Paper 4'})
       MERGE (paper5:Paper {name:'Paper 5'})
       MERGE (paper6:Paper {name:'Paper 6'})
       MERGE (paper1)-[:CITES]->(paper0)

       MERGE (paper2)-[:CITES]->(paper0)
       MERGE (paper2)-[:CITES]->(paper1)

       MERGE (paper3)-[:CITES]->(paper0)
       MERGE (paper3)-[:CITES]->(paper1)
       MERGE (paper3)-[:CITES]->(paper2)

       MERGE (paper4)-[:CITES]->(paper0)
       MERGE (paper4)-[:CITES]->(paper1)
       MERGE (paper4)-[:CITES]->(paper2)
       MERGE (paper4)-[:CITES]->(paper3)

       MERGE (paper5)-[:CITES]->(paper1)
       MERGE (paper5)-[:CITES]->(paper4)

       MERGE (paper6)-[:CITES]->(paper1)
       MERGE (paper6)-[:CITES]->(paper4);
    """

  val triangleCountData =
    """MERGE (alice:Person{id:"Alice"})
       MERGE (michael:Person{id:"Michael"})
       MERGE (karin:Person{id:"Karin"})
       MERGE (chris:Person{id:"Chris"})
       MERGE (will:Person{id:"Will"})
       MERGE (mark:Person{id:"Mark"})

       MERGE (michael)-[:KNOWS]->(karin)
       MERGE (michael)-[:KNOWS]->(chris)
       MERGE (will)-[:KNOWS]->(michael)
       MERGE (mark)-[:KNOWS]->(michael)
       MERGE (mark)-[:KNOWS]->(will)
       MERGE (alice)-[:KNOWS]->(michael)
       MERGE (will)-[:KNOWS]->(chris)
       MERGE (chris)-[:KNOWS]->(karin);
    """

  val linkPredictionData =
    """MERGE (zhen:Person {name: "Zhen"})
       MERGE (praveena:Person {name: "Praveena"})
       MERGE (michael:Person {name: "Michael"})
       MERGE (arya:Person {name: "Arya"})
       MERGE (karin:Person {name: "Karin"})

       MERGE (zhen)-[:FRIENDS]-(arya)
       MERGE (zhen)-[:FRIENDS]-(praveena)
       MERGE (praveena)-[:WORKS_WITH]-(karin)
       MERGE (praveena)-[:FRIENDS]-(michael)
       MERGE (michael)-[:WORKS_WITH]-(karin)
       MERGE (arya)-[:FRIENDS]-(karin)
    """

  val shortestPathData =
    """MERGE (a:Loc {name:'A'})
       MERGE (b:Loc {name:'B'})
       MERGE (c:Loc {name:'C'})
       MERGE (d:Loc {name:'D'})
       MERGE (e:Loc {name:'E'})
       MERGE (f:Loc {name:'F'})

       MERGE (a)-[:ROAD {cost:50}]->(b)
       MERGE (a)-[:ROAD {cost:50}]->(c)
       MERGE (a)-[:ROAD {cost:100}]->(d)
       MERGE (b)-[:ROAD {cost:40}]->(d)
       MERGE (c)-[:ROAD {cost:40}]->(d)
       MERGE (c)-[:ROAD {cost:80}]->(e)
       MERGE (d)-[:ROAD {cost:30}]->(e)
       MERGE (d)-[:ROAD {cost:80}]->(f)
       MERGE (e)-[:ROAD {cost:40}]->(f);
    """

  val similarityData =
    """MERGE (french:Cuisine {name:'French'})
       MERGE (italian:Cuisine {name:'Italian'})
       MERGE (indian:Cuisine {name:'Indian'})
       MERGE (lebanese:Cuisine {name:'Lebanese'})
       MERGE (portuguese:Cuisine {name:'Portuguese'})

       MERGE (zhen:Person {name: "Zhen"})
       MERGE (praveena:Person {name: "Praveena"})
       MERGE (michael:Person {name: "Michael"})
       MERGE (arya:Person {name: "Arya"})
       MERGE (karin:Person {name: "Karin"})

       MERGE (praveena)-[:LIKES]->(indian)
       MERGE (praveena)-[:LIKES]->(portuguese)

       MERGE (zhen)-[:LIKES]->(french)
       MERGE (zhen)-[:LIKES]->(indian)

       MERGE (michael)-[:LIKES]->(french)
       MERGE (michael)-[:LIKES]->(italian)
       MERGE (michael)-[:LIKES]->(indian)

       MERGE (arya)-[:LIKES]->(lebanese)
       MERGE (arya)-[:LIKES]->(italian)
       MERGE (arya)-[:LIKES]->(portuguese)

       MERGE (karin)-[:LIKES]->(lebanese)
       MERGE (karin)-[:LIKES]->(italian)
    """
}
