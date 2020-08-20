package neotypes


import neotypes.implicits.mappers.parameters._
import neotypes.implicits.syntax.cypher._
import neotypes.types.QueryParam
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._

final class CypherQueryInterpolationSpec extends AnyWordSpec {
  "cypher" should {
    "interpolate with one param" in {
      val name = "John"
      val query = c"create (a:Test {name: $name})"

      val expected = DeferredQuery(
        query  = "create (a:Test {name: $p1})",
        params = Map("p1" -> QueryParam("John"))
      )
      query.query shouldBe expected
    }

    "interpolate with no params" in {
      val query = c"""create (a:Test {name: "test"})"""

      val expected = DeferredQuery(
        query = """create (a:Test {name: "test"})""",
        params = Map.empty
      )

      query.query shouldBe expected
    }

    "concat DeferredQueryBuilders" in {
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

      query1.query shouldBe expected1
      query2.query shouldBe expected2
      query3.query shouldBe expected3
    }

    "concat DeferredQueryBuilder with String" in {
      val name = "John"
      val query = c"""create (a:Test {name: $name,""" + "born: 1980})"

      val expected = DeferredQuery(
        query  = """create (a:Test {name: $p1, born: 1980})""",
        params = Map("p1" -> QueryParam("John"))
      )

      query.query shouldBe expected
    }

    "concat multiple parts correctly" in {
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

      query.query shouldBe expected
    }
  }
}
