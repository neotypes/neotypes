package neotypes

import org.scalatest.FlatSpec

import neotypes.implicits._

class CypherQueryInterpolationSpec extends FlatSpec {
  it should "interpolation with one param" in {
    val name = "John"
    val query = c"create (a:Test {name: $name})"

    val expected = DeferredQuery(
      query  = "create (a:Test {name: $p1})",
      params = Map("p1" -> "John")
    )

    assert(query.query == expected)
  }

  it should "interpolation with no params" in {
    val query = c"""create (a:Test {name: "test"})"""

    val expected = DeferredQuery(
      query = """create (a:Test {name: "test"})"""
    )

    assert(query.query == expected)
  }

  it should "concat DeferredQueryBuilders" in {
    val name = "John"
    val born = 1980

    val query1 = c"""create (a:Test {name: $name,""" + c"born: $born})"
    val query2 = c"""create (a:Test {name: "John",""" + c"born: $born})"
    val query3 = c"""create (a:Test {name: $name,""" + c"born: 1980})"

    val expected1 = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: $p2})""",
      params = Map("p1" -> "John", "p2" -> 1980)
    )
    val expected2 = DeferredQuery(
      query  = """create (a:Test {name: "John", born: $p1})""",
      params = Map("p1" -> 1980)
    )
    val expected3 = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: 1980})""",
      params = Map("p1" -> "John")
    )

    assert(query1.query == expected1)
    assert(query2.query == expected2)
    assert(query3.query == expected3)
  }

  it should "concat DeferredQueryBuilder with String" in {
    val name = "John"
    val query = c"""create (a:Test {name: $name,""" + "born: 1980})"

    val expected = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: 1980})""",
      params = Map("p1" -> "John")
    )

    assert(query.query == expected)
  }

  it should "concat multiple parts correctly" in {
    val firstName = "John"
    val lastName = "Smith"
    val born = 1980

    val query =
      c"create (a:Test {" +
      c"firstName: $firstName," +
      c"lastName: $lastName," +
      """city: "Filadelfia",""" +
      c"born: $born" +
      "})"

    val expected = DeferredQuery(
      query  = """create (a:Test { firstName: $p1, lastName: $p2, city: "Filadelfia", born: $p3 })""",
      params = Map("p1" -> "John", "p2" -> "Smith", "p3" -> 1980)
    )

    assert(query.query == expected)
  }
}
