package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import org.scalatest.matchers.should.Matchers

/** Base class for testing the use of the library with the Neo4j graph-data-science plugin. */
final class AlgorithmSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with CleaningIntegrationSpec[F] with Matchers {
  behavior of s"The Neo4j graph-data-science plugin used with: ${effectName}"

  import AlgorithmData._

  it should "execute the article rank centrality algorithm" in executeAsFuture { d =>
    for {
      _ <- articleRankingData.query[Unit].execute(d)
      _ <- """CALL gds.graph.project(
                'papersGraph',
                'Paper',
                'CITES'
              )
           """.query[Unit].execute(d)
      result <- """CALL gds.articleRank.stream('papersGraph', {
                     maxIterations: 20,
                     dampingFactor: 0.85,
                     tolerance: 0.0000001,
                     scaler: 'L1Norm'
                   })
                   YIELD nodeId, score AS rawScore
                   RETURN
                     gds.util.asNode(nodeId).name AS paper,
                     round(100 * rawScore) AS score
                   ORDER BY rawScore DESC
                   LIMIT 5
                """.query[ScoredPaper].list(d)
      _ <- "CALL gds.graph.drop('papersGraph', false)".query[Unit].execute(d)
    } yield {
      result shouldBe List(
        ScoredPaper(paper = "Paper 0", score = 22),
        ScoredPaper(paper = "Paper 1", score = 20),
        ScoredPaper(paper = "Paper 4", score = 14),
        ScoredPaper(paper = "Paper 2", score = 13),
        ScoredPaper(paper = "Paper 3", score = 11)
      )
    }
  }

  it should "execute the triangle count community detection algorithm" in executeAsFuture { d =>
    for{
      _ <- triangleCountData.query[Unit].execute(d)
      _ <- """CALL gds.graph.project(
                'peopleGraph',
                'Person',
                {
                  KNOWS: {
                    orientation: 'UNDIRECTED'
                  }
                }
              )
           """.query[Unit].execute(d)
      result <- """CALL gds.triangleCount.mutate('peopleGraph', {
                     mutateProperty: 'tc'
                   })
                   YIELD globalTriangleCount
                   CALL gds.localClusteringCoefficient.stream('peopleGraph', {
                     triangleCountProperty: 'tc'
                   })
                   YIELD nodeId, localClusteringCoefficient
                   RETURN
                     gds.util.asNode(nodeId).name AS person,
                     round(100 * localClusteringCoefficient) AS coefficient,
                     gds.util.nodeProperty('peopleGraph', nodeId, 'tc') AS triangleCount
                   ORDER BY
                     coefficient DESC,
                     triangleCount DESC,
                     person ASC
                   LIMIT 5
                """.query[PersonTriangleCount].list(d)
      _ <- "CALL gds.graph.drop('peopleGraph', false)".query[Unit].execute(d)
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

  it should "execute the adamic adar link prediction algorithm" in executeAsFuture { d =>
    for{
      _ <- linkPredictionData.query[Unit].execute(d)
      result <- """MATCH (p1: Person { name: 'Michael' })
                   MATCH (p2: Person { name: 'Karin' })
                   RETURN round(100 * gds.alpha.linkprediction.adamicAdar(p1, p2))
                """.query[Int].single(d)
    } yield {
      result shouldBe 91
    }
  }

  it should "execute the shortest path algorithm" in executeAsFuture { d =>
    for{
      _ <- shortestPathData.query[Unit].execute(d)
      _ <- """CALL gds.graph.project(
                'locationsGraph',
                'Location',
                'ROAD',
                {
                  relationshipProperties: 'cost'
                }
              )
           """.query[Unit].execute(d)
      result <- """MATCH (start: Location { name:'A' }), (target: Location { name:'F' })
                   CALL gds.shortestPath.dijkstra.stream('locationsGraph', {
                     sourceNode: id(start),
                     targetNode: id(target),
                     relationshipWeightProperty: 'cost'
                   })
                   YIELD nodeIds, totalCost
                   RETURN
                     totalCost,
                     [nodeId IN nodeIds | gds.util.asNode(nodeId).name] AS nodeNames
                """.query[ShortestPath].single(d)
      _ <- "CALL gds.graph.drop('locationsGraph', false)".query[Unit].execute(d)
    } yield {
      result shouldBe ShortestPath(
        nodeNames = List("A", "B", "D", "E", "F"),
        totalCost = 160
      )
    }
  }

  it should "execute the Jaccard similarity algorithm" in executeAsFuture { d =>
    for{
      _ <- similarityData.query[Unit].execute(d)
      result <- """MATCH (p1: Person { name: 'Karin' })-[: LIKES]->(cuisine1)
                   WITH p1, collect(id(cuisine1)) AS p1Cuisine
                   MATCH (p2: Person { name: "Arya" })-[: LIKES]->(cuisine2)
                   WITH p1, p1Cuisine, p2, collect(id(cuisine2)) AS p2Cuisine
                   RETURN round(100 * gds.similarity.jaccard(p1Cuisine, p2Cuisine))
                """.query[Int].single(d)
    } yield {
      result shouldBe 67
    }
  }
}

object AlgorithmData {
  final case class ScoredPaper(paper:  String, score:  Int)
  final case class PersonTriangleCount(person:  String, triangleCount:  Int, coefficient:  Int)
  final case class ShortestPath(nodeNames: List[String], totalCost: Int)

  val articleRankingData =
    """CREATE
       (paper0: Paper { name: 'Paper 0' }),
       (paper1: Paper { name: 'Paper 1' }),
       (paper2: Paper { name: 'Paper 2' }),
       (paper3: Paper { name: 'Paper 3' }),
       (paper4: Paper { name: 'Paper 4' }),
       (paper5: Paper { name: 'Paper 5' }),
       (paper6: Paper { name: 'Paper 6' }),
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
       (zhen: Person { name:  "Zhen" }),
       (praveena: Person { name:  "Praveena" }),
       (michael: Person { name:  "Michael" }),
       (arya: Person { name:  "Arya" }),
       (karin: Person { name:  "Karin" }),

       (zhen)-[: FRIENDS]->(arya),
       (zhen)-[: FRIENDS]->(praveena),
       (praveena)-[: WORKS_WITH]->(karin),
       (praveena)-[: FRIENDS]->(michael),
       (michael)-[: WORKS_WITH]->(karin),
       (arya)-[: FRIENDS]->(karin)
    """

  val shortestPathData =
    """CREATE
       (a: Location { name: 'A' }),
       (b: Location { name: 'B' }),
       (c: Location { name: 'C' }),
       (d: Location { name: 'D' }),
       (e: Location { name: 'E' }),
       (f: Location { name: 'F' }),

       (a)-[: ROAD { cost: 50 }]->(b),
       (a)-[: ROAD { cost: 60 }]->(c),
       (a)-[: ROAD { cost: 100 }]->(d),
       (b)-[: ROAD { cost: 40 }]->(d),
       (c)-[: ROAD { cost: 50 }]->(d),
       (c)-[: ROAD { cost: 80 }]->(e),
       (d)-[: ROAD { cost: 30 }]->(e),
       (d)-[: ROAD { cost: 80 }]->(f),
       (e)-[: ROAD { cost: 40 }]->(f)
    """

  val similarityData =
    """CREATE
       (french: Cuisine { name: 'French' }),
       (italian: Cuisine { name: 'Italian' }),
       (indian: Cuisine { name: 'Indian' }),
       (lebanese: Cuisine { name: 'Lebanese' }),
       (portuguese: Cuisine { name: 'Portuguese' }),

       (zhen: Person { name:  "Zhen" }),
       (praveena: Person { name:  "Praveena" }),
       (michael: Person { name:  "Michael" }),
       (arya: Person { name:  "Arya" }),
       (karin: Person { name:  "Karin" }),

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
