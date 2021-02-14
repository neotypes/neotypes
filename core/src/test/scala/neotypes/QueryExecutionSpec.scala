package neotypes

import neotypes.generic.auto._
import neotypes.implicits.mappers.collections._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import scala.collection.immutable.{ListMap, ListSet, SortedMap}
import org.scalatest.matchers.should.Matchers._

/** Base class for testing the different ways of executing queries. */
final class QueryExecutionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  s"Executing queries using: ${effectName}" should {
    "retrieve multiple results as a List" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name"
        .query[Int]
        .list(d)
        .map {
          _ shouldBe (0 to 10).toList
        }
    }
    "retrieve multiple results as a Set" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name"
        .query[Int]
        .set(d)
        .map {
          _ shouldBe (0 to 10).toSet
        }
    }
    "retrieve multiple results as a Vector" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name"
        .query[Int]
        .vector(d)
        .map {
          _ shouldBe (0 to 10).toVector
        }
    }
    "retrieve multiple results as a Map" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name, 1"
        .query[(Int, Int)]
        .map(d)
        .map {
          _ shouldBe (0 to 10).map(k => k -> 1).toMap
        }
    }
    "retrieve multiple results as a custom collection (ListSet)" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name"
        .query[Int]
        .collectAs(ListSet)(d)
        .map {
          _ shouldBe (0 to 10).to(ListSet)
        }
    }
    "retrieve multiple results as a custom collection (ListMap)" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name, 1"
        .query[(Int, Int)]
        .collectAs(ListMap)(d)
        .map {
          _ shouldBe (0 to 10).map(_ -> 1).to(ListMap)
        }
    }
    "retrieve multiple results as a custom collection (SortedMap)" in executeAsFuture { d =>
      "MATCH (p: Person) RETURN p.name, 1"
        .query[(Int, Int)]
        .collectAs(SortedMap)(d)
        .map {
          _ shouldBe (0 to 10).map(_ -> 1).to(SortedMap)
        }
    }
    "retrieve a Neo4j LIST into a Scala collection (List)" in executeAsFuture { d =>
      "unwind [1, 2] as x RETURN x"
        .query[Int]
        .list(d)
        .map {
          _ shouldBe List(1, 2)
        }
    }
    "retrieve a Neo4j LIST into a Scala collection (ListSet)" in executeAsFuture { d =>
      "unwind [1, 2] as x RETURN x"
        .query[Int]
        .collectAs(ListSet)(d)
        .map {
          _ shouldBe ListSet(1, 2)
        }
    }
  }

  override final val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
