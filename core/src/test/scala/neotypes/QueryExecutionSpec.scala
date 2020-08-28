package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import scala.collection.immutable.{ListMap, ListSet, SortedMap}
import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers._


/** Base classs for testing the different ways of executing queries. */
final class QueryExecutionSpec[F[_]](testkit: EffectTestkit[F]) extends BaseIntegrationWordSpec(testkit) {
  s"Excuting queries using: ${effectName}" should {
    "retrieve multiple results as a List" in executeAsFuture { s =>
      "match (p:Person) return p.name"
        .query[Int]
        .list(s)
        .map {
          names => names shouldBe (0 to 10).toList
        }
    }

    "retrieve multiple results as a Set" in executeAsFuture { s =>
      "match (p:Person) return p.name"
        .query[Int]
        .set(s)
        .map {
          names => names shouldBe (0 to 10).toSet
        }
    }

    "retrieve multiple results as a Vector" in executeAsFuture { s =>
      "match (p:Person) return p.name"
        .query[Int]
        .vector(s)
        .map {
          names => names shouldBe (0 to 10).toVector
        }
    }

    "retrieve multiple results as a Map" in executeAsFuture { s =>
      "match (p:Person) return p.name, 1"
        .query[(Int, Int)]
        .map(s)
        .map {
          names => names shouldBe (0 to 10).map(k => k -> 1).toMap
        }
    }

    "retrieve multiple results as a custom collection (ListSet)" in executeAsFuture { s =>
      "match (p:Person) return p.name"
        .query[Int]
        .collectAs(ListSet)(s)
        .map {
          names => names shouldBe ListSet((0 to 10) : _*)
        }
    }

    "retrieve multiple results as a custom collection (ListMap)" in executeAsFuture { s =>
      "match (p:Person) return p.name, 1"
        .query[(Int, Int)]
        .collectAs(ListMap)(s)
        .map {
          names => names shouldBe ListMap((0 to 10).map(k => k -> 1) : _*)
        }
    }

    "retrieve multiple results as a custom collection (SortedMap)" in executeAsFuture { s =>
      "match (p:Person) return p.name, 1"
        .query[(Int, Int)]
        .collectAs(SortedMap)(s)
        .map {
          names => names shouldBe SortedMap((0 to 10).map(k => k -> 1) : _*)
        }
    }

    "retrieve a Neo4j LIST into a Scala collection (List)" in executeAsFuture { s =>
      "unwind [1, 2] as x return x"
        .query[Int]
        .list(s)
        .map {
          names => names shouldBe List(1, 2)
        }
    }

    "retrieve a Neo4j LIST into a Scala collection (ListSet)" in executeAsFuture { s =>
      "unwind [1, 2] as x return x"
        .query[Int]
        .collectAs(ListSet)(s)
        .map {
          names => names shouldBe ListSet(1, 2)
        }
    }
  }

  override final val initQuery: String = BaseIntegrationWordSpec.MULTIPLE_VALUES_INIT_QUERY
}
