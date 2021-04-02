package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.cypher._
import neotypes.types.QueryParam
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._

final class CypherQueryInterpolationSpec extends AnyWordSpec{
  import CypherQueryInterpolationSpec._

  "cypher" should {
    "interpolation with one param" in {
      val name = "John"
      val query = c"create (a:Test {name: $name})"

      val expected = DeferredQuery(
        query  = "create (a:Test {name: $p1})",
        params = Map("p1" -> QueryParam("John"))
      )

      query.query shouldBe expected
    }
    "interpolation with no params" in {
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
    "interpolation with a case class" in {
      val testClass = TestClass("name", 33)

      val query = c"""create (a:Test { $testClass })"""

      val expected = DeferredQuery(
        query = """create (a:Test {  name: $p1, age: $p2 })""",
        params = Map(
          "p1" -> QueryParam(testClass.name),
          "p2" -> QueryParam(testClass.age)
        )
      )

      query.query shouldBe expected
    }
    "interpolation with a case class and extra args" in {
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

      query.query shouldBe expected
    }
    "interpolation with a case class and extra args (concat queries)" in {
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

      query.query shouldBe expected
    }
    "interpolation with case classes and a relationship" in {
      val user = User("Joan", 20)
      val cat = Cat("Waffles", 3)
      val relationship = HasCatRelationship(2)

      val query = c"CREATE (u: User { $user }) -[r:HAS_CAT { $relationship }]->(c:Cat { $cat }) RETURN r"

      val expected = DeferredQuery(
        query = "CREATE (u: User {  name: $p1, age: $p2 }) -[r:HAS_CAT {  friendsFor: $p3 }]->(c:Cat {  tag: $p4, age: $p5 }) RETURN r",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age),
          "p3" -> QueryParam(relationship.friendsFor),
          "p4" -> QueryParam(cat.tag),
          "p5" -> QueryParam(cat.age)
        )
      )

      query.query shouldBe expected
    }
    "interpolation with a case classes and an extra property" in {
      val user = User("Joan", 20)
      val extraProp = 123

      val query = c"CREATE (u: User { $user, extraProperty: $extraProp }) RETURN u"

      val expected = DeferredQuery(
        query = "CREATE (u: User {  name: $p1, age: $p2, extraProperty: $p3 }) RETURN u",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age),
          "p3" -> QueryParam(extraProp)
        )
      )

      query.query shouldBe expected
    }
  }
}

object CypherQueryInterpolationSpec {

  case class TestClass(name: String, age: Int)

  case class User(name: String, age: Int)
  case class Cat(tag: String, age: Int)
  case class HasCatRelationship(friendsFor: Int)

}
