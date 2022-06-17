package neotypes

import neotypes.generic.auto._
import neotypes.implicits.mappers.collections._
import neotypes.implicits.syntax.string._
import neotypes.internal.syntax.async._
import scala.collection.immutable.{ListMap, ListSet, SortedMap}

/** Base class for testing the different ways of executing queries. */
final class QueryExecutionSpec[F[_]](testkit: EffectTestkit[F]) extends AsyncDriverProvider[F](testkit) with BaseIntegrationSpec[F] {
  behavior of s"Executing queries using: ${effectName}"

  it should "retrieve multiple results as a List" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name"
      .readOnlyQuery[Int]
      .list(d)
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  it should "retrieve multiple results as a Set" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name"
      .readOnlyQuery[Int]
      .set(d)
      .map {
        names => assert(names == (0 to 10).toSet)
      }
  }

  it should "retrieve multiple results as a Vector" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name"
      .readOnlyQuery[Int]
      .vector(d)
      .map {
        names => assert(names == (0 to 10).toVector)
      }
  }

  it should "retrieve multiple results as a Map" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name, 1"
      .readOnlyQuery[(Int, Int)]
      .map(d)
      .map {
        names => assert(names == (0 to 10).map(k => k -> 1).toMap)
      }
  }

  it should "retrieve multiple results as a custom collection (ListSet)" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name"
      .readOnlyQuery[Int]
      .collectAs(ListSet)(d)
      .map {
        names => assert(names == (0 to 10).to(ListSet))
      }
  }

  it should "retrieve multiple results as a custom collection (ListMap)" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name, 1"
      .readOnlyQuery[(Int, Int)]
      .collectAs(ListMap)(d)
      .map {
        names => assert(names == (0 to 10).map(_ -> 1).to(ListMap))
      }
  }

  it should "retrieve multiple results as a custom collection (SortedMap)" in executeAsFuture { d =>
    "MATCH (p: Person) RETURN p.name, 1"
      .readOnlyQuery[(Int, Int)]
      .collectAs(SortedMap)(d)
      .map {
        names => assert(names == (0 to 10).map(_ -> 1).to(SortedMap))
      }
  }

  it should "retrieve a Neo4j LIST into a Scala collection (List)" in executeAsFuture { d =>
    "unwind [1, 2] as x RETURN x"
      .readOnlyQuery[Int]
      .list(d)
      .map {
        names => assert(names == List(1, 2))
      }
  }

  it should "retrieve a Neo4j LIST into a Scala collection (ListSet)" in executeAsFuture { d =>
    "unwind [1, 2] as x RETURN x"
      .readOnlyQuery[Int]
      .collectAs(ListSet)(d)
      .map {
        names => assert(names == ListSet(1, 2))
      }
  }

  override final val initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
