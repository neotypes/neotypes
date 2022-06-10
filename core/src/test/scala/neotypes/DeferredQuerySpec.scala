package neotypes

import neotypes.implicits.syntax.cypher._
import neotypes.types.QueryParam
import org.scalatest.flatspec.AnyFlatSpec

final class DeferredQuerySpec extends AnyFlatSpec {
  it should "prefix query parameter names" in {
    val p1 = 1
    val p2 = "foo"

    // No parameters
    val query0 = c"CREATE (a: Test { id: 1 })".query[Unit]
    assert(query0 == query0)

    // Single parameter
    val query1 = c"CREATE (a: Test { id: ${p1} })".query[Unit]
    val expected1 = DeferredQuery(
      query = "CREATE (a: Test { id: $q1_p1 })",
      params = Map("q1_p1" -> QueryParam(p1))
    )
    assert(query1 == expected1)

    // Multiple parameters
    val query2 = c"CREATE (a: Test { id: ${p1}, name: ${p2} })".query[Unit]
    val expected2 = DeferredQuery(
      query = "CREATE (a: Test { id: $q1_p1, name: $q1_p2 })",
      params = Map("q1_p1" -> QueryParam(p1), "q1_p2" -> QueryParam(p2))
    )
    assert(query2 == expected2)
  }
}
