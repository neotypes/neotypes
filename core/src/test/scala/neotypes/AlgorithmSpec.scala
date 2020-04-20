package neotypes

import org.scalatest.Matchers
import neotypes.implicits.mappers.all._
import neotypes.implicits.syntax.string._

import scala.concurrent.Future
import scala.reflect.ClassTag


abstract class AlgorithmSpec[F[_]](implicit ctF: ClassTag[F[_]]) extends BaseAlgorithmIntegrationSpec[F] with Matchers {
  import AlgorithmData._
  private val effectName: String = ctF.runtimeClass.getCanonicalName
  behavior of s"Effect[${effectName}]"

  def fToFuture[T](f: F[T]): Future[T]

  implicit def F: Async[F]

  private final def executeAsFuture[T](work: Session[F] => F[T]): Future[T] =
    fToFuture(execute(work))

  it should "execute the article rank centrality algorithm" in {
    for {
      _ <- executeAsFuture (s => articleRankingData.query[Unit].execute(s))
      r <- executeAsFuture {s => """CALL algo.articleRank('Paper', 'CITES',
      {iterations:20, dampingFactor:0.85, write: true,writeProperty:"pagerank"})
      YIELD nodes, iterations, dampingFactor, write, writeProperty""".query[ArticleRanking].single (s)}
    } yield r shouldBe ArticleRanking(7, 20, 0.85, true, "pagerank")
  }

  it should "execute the triangle count community detection algorithm" in {
    for{
      _ <- executeAsFuture(s => triangleCountData.query[Unit].execute(s))
      r <- executeAsFuture{s => """CALL algo.triangleCount('Person', 'KNOWS',
      {concurrency:4, write:true, writeProperty:'triangles',clusteringCoefficientProperty:'coefficient'})
      YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient;""".query[TriangleCount].single(s)}
    } yield r shouldBe TriangleCount(6, 3, 0.6055555555555555)
  }

  it should "execute the adamic adar link prediction algorithm" in {
    for{
      _ <- executeAsFuture(s => linkPredictionData.query[Unit].execute(s))
      r <- executeAsFuture{s => """MATCH (p1:Person {name: 'Michael'})
      MATCH (p2:Person {name: 'Karin'})
      RETURN algo.linkprediction.adamicAdar(p1, p2) AS score""".query[AdamicLink].single(s) }
    } yield r shouldBe AdamicLink(0.9102392266268373)
  }

  it should "execute the shortest path algorithm" in {
    for{
      _ <- executeAsFuture(s => shortestPathData.query[Unit].execute(s))
      r <- executeAsFuture{s => """MATCH (start:Loc{name:'A'}), (end:Loc{name:'F'})
      CALL algo.shortestPath(start, end, 'cost',{write:true,writeProperty:'sssp'})
      YIELD writeMillis,loadMillis,nodeCount, totalCost
      RETURN writeMillis,loadMillis,nodeCount,totalCost""".query[ShortestPath].single(s) }
    } yield r shouldBe ShortestPath(5, 160.0)
  }

  it should "execute the jaccard similarity algorithm" in {
    for{
      _ <- executeAsFuture(s => similarityData.query[Unit].execute(s))
      r <- executeAsFuture{s => """MATCH (p1:Person {name: 'Karin'})-[:LIKES]->(cuisine1)
      WITH p1, collect(id(cuisine1)) AS p1Cuisine
      MATCH (p2:Person {name: "Arya"})-[:LIKES]->(cuisine2)
      WITH p1, p1Cuisine, p2, collect(id(cuisine2)) AS p2Cuisine
      RETURN p1.name AS from,
             p2.name AS to,
             algo.similarity.jaccard(p1Cuisine, p2Cuisine) AS similarity""".query[JaccardSimilarity].single(s) }
    } yield r shouldBe JaccardSimilarity("Karin", "Arya", 0.6666666666666666)
  }
}

object AlgorithmData{
  case class ArticleRanking(nodes: Int, iterations: Int, dampingFactor: Double, write: Boolean, writeProperty: String)
  case class TriangleCount(nodeCount: Int, triangleCount: Int, averageClusteringCoefficient: Double)
  case class AdamicLink(score: Double)
  case class ShortestPath(nodeCount: Int, totalCost: Double)
  case class JaccardSimilarity(from: String, to: String, similarity: Double)

  val articleRankingData =
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

  val triangleCountData =
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

  val linkPredictionData =
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

  val shortestPathData =
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

  val similarityData =
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
}
