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

  it should "interpolate DeferredQueryBuilders" in {
    val subQuery1 = c"""user.id = "1""""
    val query1 = c"""MATCH (user:User) WHERE $subQuery1 RETURN user"""
    val expected1 = DeferredQuery(
      query = """MATCH (user:User) WHERE  user.id = "1"  RETURN user""",
      params = Map.empty
    )
    assert(query1.query == expected1)

    val subQuery2Param1 = 1
    val subQuery2Param2 = false
    val subQuery2 = c"""user.id = $subQuery2Param1 AND $subQuery2Param2"""
    val query2 = c"""MATCH (user:User) WHERE $subQuery2 RETURN user"""
    val expected2 = DeferredQuery(
      query = """MATCH (user:User) WHERE  user.id = $q1_p1 AND $q1_p2 RETURN user""",
      params = Map("q1_p1" -> QueryParam(subQuery2Param1), "q1_p2" -> QueryParam(subQuery2Param2))
    )
    assert(query2.query == expected2)
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

  it should "interpolation with case classes and a relationship" in {
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

    assert(query.query == expected)
  }

  it should "interpolation with a case classes and an extra property" in {
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

    assert(query.query == expected)
  }
}

object CypherQueryInterpolationSpec {

  case class TestClass(name: String, age: Int)

  case class User(name: String, age: Int)
  case class Cat(tag: String, age: Int)
  case class HasCatRelationship(friendsFor: Int)

}
