package neotypes

import neotypes.generic.auto._
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import org.scalatest.matchers.should.Matchers

/** Base class for testing the use of the library with the Neo4j graph-data-science plugin. */
final class AlgorithmSpec[F[_]](testkit: EffectTestkit[F]) extends CleaningIntegrationSpec(testkit) with Matchers {
  behavior of s"The Neo4j graph-data-science plugin used with: ${effectName}"

  import AlgorithmData._

  it should "execute the article rank centrality algorithm" in executeAsFuture { s =>
    for {
      _ <- articleRankingData.query[Unit].execute(s)
      result <- """CALL gds.alpha.articleRank.stream({
                     nodeProjection: "Paper",
                     relationshipProjection: "CITES",
                     maxIterations: 20,
                     dampingFactor: 0.85,
                     writeProperty: "pagerank"
                   })
                   YIELD nodeId, score AS rawScore
                   RETURN
                     gds.util.asNode(nodeId).name AS paper,
                     round(100 * rawScore) AS score
                   ORDER BY rawScore DESC
                   LIMIT 5
                """.query[ScoredPaper].list(s)
    } yield {
      result shouldBe List(
        ScoredPaper(paper = "Paper 0", score = 35),
        ScoredPaper(paper = "Paper 1", score = 32),
        ScoredPaper(paper = "Paper 4", score = 21),
        ScoredPaper(paper = "Paper 2", score = 21),
        ScoredPaper(paper = "Paper 3", score = 18)
      )
    }
  }

  it should "execute the triangle count community detection algorithm" in executeAsFuture { s =>
    for{
      _ <- triangleCountData.query[Unit].execute(s)
      _ <- """CALL gds.graph.create(
                'myGraph',
                'Person',
                {
                  KNOWS: {
                    orientation: 'UNDIRECTED'
                  }
                }
              )
           """.query[Unit].execute(s)
      result <- """CALL gds.triangleCount.mutate('myGraph', {mutateProperty: 'tc'})
                   YIELD globalTriangleCount
                   CALL gds.localClusteringCoefficient.stream(
                     'myGraph', {
                     triangleCountProperty: 'tc'
                   }) YIELD nodeId, localClusteringCoefficient
                   WITH
                     round(100 * localClusteringCoefficient) AS coefficient,
                     gds.util.asNode(nodeId).name AS person,
                     gds.util.nodeProperty('myGraph', nodeId, 'tc') AS triangleCount
                   RETURN person, triangleCount, coefficient
                   ORDER BY
                     coefficient DESC,
                     triangleCount DESC,
                     person ASC
                   LIMIT 5
                """.query[PersonTriangleCount].list(s)
    } yield {
      result shouldBe List(
        PersonTriangleCount(person = "Karin", triangleCount = 1, coefficient = 100),
        PersonTriangleCount(person = "Mark", triangleCount = 1, coefficient = 100),
        PersonTriangleCount(person = "Chris", triangleCount = 2, coefficient = 67),
        PersonTriangleCount(person = "Will", triangleCount = 2, coefficient = 67),
        PersonTriangleCount(person = "Michael", triangleCount = 3, coefficient = 30)
      )
    }
  }

  it should "execute the adamic adar link prediction algorithm" in executeAsFuture { s =>
    for{
      _ <- linkPredictionData.query[Unit].execute(s)
      result <- """MATCH (p1: Person { name: 'Michael' })
                   MATCH (p2: Person { name: 'Karin' })
                   RETURN round(100 * gds.alpha.linkprediction.adamicAdar(p1, p2))
                """.query[Int].single(s)
    } yield {
      result shouldBe 91
    }
  }

  it should "execute the shortest path algorithm" in executeAsFuture { s =>
    for{
      _ <- shortestPathData.query[Unit].execute(s)
      result <- """MATCH (start: Loc { name:'A' }), (end: Loc { name:'F' })
                   CALL gds.alpha.shortestPath.write({
                     nodeProjection: 'Loc',
                     relationshipProjection: {
                       ROAD: {
                         type: 'ROAD',
                         properties: 'cost',
                         orientation: 'UNDIRECTED'
                       }
                     },
                     startNode: start,
                     endNode: end,
                     relationshipWeightProperty: 'cost'
                   })
                   YIELD nodeCount, totalCost
                   RETURN nodeCount, totalCost
                """.query[ShortestPath].single(s)
    } yield {
      result shouldBe ShortestPath(
        nodeCount = 5,
        totalCost = 160
      )
    }
  }

  it should "execute the jaccard similarity algorithm" in executeAsFuture { s =>
    for{
      _ <- similarityData.query[Unit].execute(s)
      result <- """MATCH (p1: Person { name: 'Karin' })-[: LIKES]->(cuisine1)
                   WITH p1, collect(id(cuisine1)) AS p1Cuisine
                   MATCH (p2: Person { name: "Arya" })-[: LIKES]->(cuisine2)
                   WITH p1, p1Cuisine, p2, collect(id(cuisine2)) AS p2Cuisine
                   RETURN round(100 * gds.alpha.similarity.jaccard(p1Cuisine, p2Cuisine))
                """.query[Int].single(s)
    } yield {
      result shouldBe 67
    }
  }
}

object AlgorithmData {
  final case class ScoredPaper(paper:  String, score:  Int)
  final case class PersonTriangleCount(person:  String, triangleCount:  Int, coefficient:  Int)
  final case class ShortestPath(nodeCount: Int, totalCost: Int)

  val articleRankingData =
    """CREATE
       (paper0: Paper  { name: 'Paper 0' }),
       (paper1: Paper  { name: 'Paper 1' }),
       (paper2: Paper  { name: 'Paper 2' }),
       (paper3: Paper  { name: 'Paper 3' }),
       (paper4: Paper  { name: 'Paper 4' }),
       (paper5: Paper  { name: 'Paper 5' }),
       (paper6: Paper  { name: 'Paper 6' }),
       (paper1)-[: CITES]->(paper0),

       (paper2)-[: CITES]->(paper0),
       (paper2)-[: CITES]->(paper1),

       (paper3)-[: CITES]->(paper0),
       (paper3)-[: CITES]->(paper1),
       (paper3)-[: CITES]->(paper2),

       (paper4)-[: CITES]->(paper0),
       (paper4)-[: CITES]->(paper1),
       (paper4)-[: CITES]->(paper2),
       (paper4)-[: CITES]->(paper3),

       (paper5)-[: CITES]->(paper1),
       (paper5)-[: CITES]->(paper4),

       (paper6)-[: CITES]->(paper1),
       (paper6)-[: CITES]->(paper4)
    """

  val triangleCountData =
    """CREATE
       (alice: Person { name: "Alice" }),
       (michael: Person { name: "Michael" }),
       (karin: Person { name: "Karin" }),
       (chris: Person { name: "Chris" }),
       (will: Person { name: "Will" }),
       (mark: Person { name: "Mark" }),

       (michael)-[: KNOWS]->(karin),
       (michael)-[: KNOWS]->(chris),
       (will)-[: KNOWS]->(michael),
       (mark)-[: KNOWS]->(michael),
       (mark)-[: KNOWS]->(will),
       (alice)-[: KNOWS]->(michael),
       (will)-[: KNOWS]->(chris),
       (chris)-[: KNOWS]->(karin)
    """

  val linkPredictionData =
    """CREATE
       (zhen: Person  { name:  "Zhen" }),
       (praveena: Person  { name:  "Praveena" }),
       (michael: Person  { name:  "Michael" }),
       (arya: Person  { name:  "Arya" }),
       (karin: Person  { name:  "Karin" }),

       (zhen)-[: FRIENDS]->(arya),
       (zhen)-[: FRIENDS]->(praveena),
       (praveena)-[: WORKS_WITH]->(karin),
       (praveena)-[: FRIENDS]->(michael),
       (michael)-[: WORKS_WITH]->(karin),
       (arya)-[: FRIENDS]->(karin)
    """

  val shortestPathData =
    """CREATE
       (a: Loc  { name: 'A' }),
       (b: Loc  { name: 'B' }),
       (c: Loc  { name: 'C' }),
       (d: Loc  { name: 'D' }),
       (e: Loc  { name: 'E' }),
       (f: Loc  { name: 'F' }),

       (a)-[: ROAD  { cost: 50 }]->(b),
       (a)-[: ROAD  { cost: 50 }]->(c),
       (a)-[: ROAD  { cost: 100 }]->(d),
       (b)-[: ROAD  { cost: 40 }]->(d),
       (c)-[: ROAD  { cost: 40 }]->(d),
       (c)-[: ROAD  { cost: 80 }]->(e),
       (d)-[: ROAD  { cost: 30 }]->(e),
       (d)-[: ROAD  { cost: 80 }]->(f),
       (e)-[: ROAD  { cost: 40 }]->(f)
    """

  val similarityData =
    """CREATE
       (french: Cuisine  { name: 'French' }),
       (italian: Cuisine  { name: 'Italian' }),
       (indian: Cuisine  { name: 'Indian' }),
       (lebanese: Cuisine  { name: 'Lebanese' }),
       (portuguese: Cuisine  { name: 'Portuguese' }),

       (zhen: Person  { name:  "Zhen" }),
       (praveena: Person  { name:  "Praveena" }),
       (michael: Person  { name:  "Michael" }),
       (arya: Person  { name:  "Arya" }),
       (karin: Person  { name:  "Karin" }),

       (praveena)-[: LIKES]->(indian),
       (praveena)-[: LIKES]->(portuguese),

       (zhen)-[: LIKES]->(french),
       (zhen)-[: LIKES]->(indian),

       (michael)-[: LIKES]->(french),
       (michael)-[: LIKES]->(italian),
       (michael)-[: LIKES]->(indian),

       (arya)-[: LIKES]->(lebanese),
       (arya)-[: LIKES]->(italian),
       (arya)-[: LIKES]->(portuguese),

       (karin)-[: LIKES]->(lebanese),
       (karin)-[: LIKES]->(italian)
    """
}
