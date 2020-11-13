package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.cypher._
import neotypes.types.QueryParam
import org.scalatest.flatspec.AnyFlatSpec

final class CypherQueryInterpolationSpec extends AnyFlatSpec {
  import CypherQueryInterpolationSpec._

  it should "interpolation with one param" in {
    val name = "John"
    val query = c"create (a:Test {name: $name})"

    val expected = DeferredQuery(
      query  = "create (a:Test {name: $p1})",
      params = Map("p1" -> QueryParam("John"))
    )

    assert(query.query == expected)
  }

  it should "interpolation with no params" in {
    val query = c"""create (a:Test {name: "test"})"""

    val expected = DeferredQuery(
      query = """create (a:Test {name: "test"})""",
      params = Map.empty
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
      params = Map("p1" -> QueryParam("John"), "p2" -> QueryParam(1980))
    )
    val expected2 = DeferredQuery(
      query  = """create (a:Test {name: "John", born: $p1})""",
      params = Map("p1" -> QueryParam(1980))
    )
    val expected3 = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: 1980})""",
      params = Map("p1" -> QueryParam("John"))
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
      params = Map("p1" -> QueryParam("John"))
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
      params = Map("p1" -> QueryParam("John"), "p2" -> QueryParam("Smith"), "p3" -> QueryParam(1980))
    )

    assert(query.query == expected)
  }

  it should "interpolation with a case class" in {
    val testClass = TestClass("name", 33)

    val query = c"""create (a:Test { $testClass })"""

    val expected = DeferredQuery(
      query = """create (a:Test {  name: $p1, age: $p2 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age)
      )
    )

    assert(query.query == expected)
  }

  it should "interpolation with a case class and extra args" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val query = c"""create (a:Test { $testClass }), (b: B { name: $bName })"""

    val expected = DeferredQuery(
      query = """create (a:Test {  name: $p1, age: $p2 }), (b: B { name: $p3 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age),
        "p3" -> QueryParam(bName)
      )
    )

    assert(query.query == expected)
  }

  it should "interpolation with a case class and extra args (concat queries)" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val query = c"""create (a:Test { $testClass }),""" + c"""(b: B { const: "Const", name: $bName })"""

    val expected = DeferredQuery(
      query = """create (a:Test {  name: $p1, age: $p2 }), (b: B { const: "Const", name: $p3 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age),
        "p3" -> QueryParam(bName)
      )
    )

    assert(query.query == expected)
  }
}

object CypherQueryInterpolationSpec {

  case class TestClass(name: String, age: Int)

}
