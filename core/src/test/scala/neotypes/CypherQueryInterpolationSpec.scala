package neotypes

import neotypes.generic.auto._
import neotypes.implicits.syntax.cypher._
import neotypes.types.QueryParam
import org.scalatest.flatspec.AnyFlatSpec

final class CypherQueryInterpolationSpec extends AnyFlatSpec {
  import CypherQueryInterpolationSpec._

  it should "interpolate no parameters" in {
    val query = c"""CREATE (a: Test { name: "John" })"""

    val expected = DeferredQuery(
      query = """CREATE (a: Test { name: "John" })""",
      params = Map.empty
    )

    assert(query.query == expected)
  }

  it should "interpolate one parameter" in {
    val name = "John"
    val query = c"CREATE (a: Test { name: ${name} })"

    val expected = DeferredQuery(
      query  = "CREATE (a: Test { name: $p1 })",
      params = Map("p1" -> QueryParam(name))
    )

    assert(query.query == expected)
  }

  it should "interpolate multiple parameters" in {
    val name = "John"
    val age = 25
    val born = 1997
    val query = c"CREATE (a: Test { name: ${name}, age: ${age}, born: ${born} })"

    val expected = DeferredQuery(
      query  = "CREATE (a: Test { name: $p1, age: $p2, born: $p3 })",
      params = Map(
        "p1" -> QueryParam(name),
        "p2" -> QueryParam(age),
        "p3" -> QueryParam(born)
      )
    )

    assert(query.query == expected)
  }

  it should "interpolate on multiline strings" in {
    val name = "John"
    val age = 25
    val born = 1997
    val query =
      c"""CREATE (a: Test {
            name: ${name},
            age: ${age},
            born: ${born}
          })"""

    val expected = DeferredQuery(
      query  =
       """CREATE (a: Test {
            name: $p1,
            age: $p2,
            born: $p3
          })""",
      params = Map(
        "p1" -> QueryParam(name),
        "p2" -> QueryParam(age),
        "p3" -> QueryParam(born)
      )
    )

    assert(query.query == expected)
  }

  it should "concat DeferredQueryBuilders" in {
    val name = "John"
    val born = 1980

    val query1 = c"""CREATE (a: Test { name: ${name},""" + c"born: ${born} })"
    val query2 = c"""CREATE (a: Test { name: "John",""" + c"born: ${born} })"
    val query3 = c"""CREATE (a: Test { name: ${name},""" + c"born: 1980 })"

    val expected1 = DeferredQuery(
      query  = "CREATE (a: Test { name: $p1, born: $p2 })",
      params = Map(
        "p1" -> QueryParam(name),
        "p2" -> QueryParam(born)
      )
    )
    val expected2 = DeferredQuery(
      query  = """CREATE (a: Test { name: "John", born: $p1 })""",
      params = Map("p1" -> QueryParam(born))
    )
    val expected3 = DeferredQuery(
      query  = """CREATE (a: Test { name: $p1, born: 1980 })""",
      params = Map("p1" -> QueryParam(name))
    )

    assert(query1.query == expected1)
    assert(query2.query == expected2)
    assert(query3.query == expected3)
  }

  it should "concat DeferredQueryBuilder with String" in {
    val name = "John"
    val query = c"CREATE (a: Test { name: ${name}," + "born: 1980 })"

    val expected = DeferredQuery(
      query  = """CREATE (a: Test { name: $p1, born: 1980 })""",
      params = Map("p1" -> QueryParam(name))
    )

    assert(query.query == expected)
  }

  it should "concat multiple parts correctly" in {
    val firstName = "John"
    val lastName = "Smith"
    val born = 1980

    val query =
      c"CREATE (a: Test {" +
      c"firstName: ${firstName}," +
      c"lastName: ${lastName}," +
      """city: "Filadelfia",""" +
      c"born: ${born}" +
      " })"

    val expected = DeferredQuery(
      query  = """CREATE (a: Test { firstName: $p1, lastName: $p2, city: "Filadelfia", born: $p3 })""",
      params = Map("p1" -> QueryParam("John"), "p2" -> QueryParam("Smith"), "p3" -> QueryParam(1980))
    )

    assert(query.query == expected)
  }

  it should "interpolate a single plain value using #$" in {
    val label = "Test"
    val query = c"""CREATE (a: #${label} { name: "John" })"""

    val expected = DeferredQuery(
      query = """CREATE (a: Test { name: "John" })""",
      params = Map.empty
    )

    assert(query.query == expected)
  }

  it should "interpolate multiple plain values using #$" in {
    val label = "Test"
    val property = "name"
    val query = c"""CREATE (a: #${label} { #${property}: "John" })"""

    val expected = DeferredQuery(
      query = """CREATE (a: Test { name : "John" })""",
      params = Map.empty
    )

    assert(query.query == expected)
  }

  it should "interpolate plain values and parameters together" in {
    val label = "Test"
    val property = "name"
    val name = "John"
    val query = c"CREATE (a: #${label} { #${property}: ${name} })"

    val expected = DeferredQuery(
      query = """CREATE (a: Test { name : $p1 })""",
      params = Map("p1" -> QueryParam(name))
    )

    assert(query.query == expected)
  }

  it should "interpolate DeferredQuery instances" in {
    // Single subquery
    locally {
      val subQuery = c"""user.id = "1""""
      val query = c"""MATCH (user:User) WHERE $subQuery RETURN user"""

      val expected = DeferredQuery(
        query = """MATCH (user:User) WHERE user.id = "1" RETURN user""",
        params = Map.empty
      )

      assert(query.query == expected)
    }

    // Subquery with parameters
    locally {
      val subQueryParam1 = 1
      val subQueryParam2 = false
      val subQuery = c"""user.id = $subQueryParam1 AND $subQueryParam2"""
      val query = c"""MATCH (user:User) WHERE $subQuery RETURN user"""

      val expected = DeferredQuery(
        query = """MATCH (user:User) WHERE user.id = $q1_p1 AND $q1_p2 RETURN user""",
        params = Map("q1_p1" -> QueryParam(subQueryParam1), "q1_p2" -> QueryParam(subQueryParam2))
      )

      assert(query.query == expected)
    }

    // Multiple nested subqueries with parameters
    locally {
      val subQuery1subQueryParam1 = 1
      val subQuery1subQueryParam2 = false
      val subQuery1subQuery = c"user.id = ${subQuery1subQueryParam1} AND ${subQuery1subQueryParam2}"

      val subQuery1Param = "foo"
      val subQuery1 = c"user.name = ${subQuery1Param} AND ${subQuery1subQuery}"

      val subQuery2subQueryParam1 = 2
      val subQuery2subQueryParam2 = true
      val subQuery2subQuery = c"user.id = ${subQuery2subQueryParam1} AND ${subQuery2subQueryParam2}"

      val subQuery2Param = "bar"
      val subQuery2 = c"user.name = ${subQuery2Param} AND ${subQuery2subQuery}"

      val query = c"MATCH (user: User) WHERE (${subQuery1}) OR (${subQuery2})"

      val expected = DeferredQuery(
        query =
          "MATCH (user: User) " +
          "WHERE " +
          "( user.name = $q1_p1 AND user.id = $q1_q1_p1 AND $q1_q1_p2 ) OR " +
          "( user.name = $q2_p1 AND user.id = $q2_q1_p1 AND $q2_q1_p2 )",
        params = Map(
          "q1_p1" -> QueryParam(subQuery1Param),
          "q1_q1_p1" -> QueryParam(subQuery1subQueryParam1),
          "q1_q1_p2" -> QueryParam(subQuery1subQueryParam2),
          "q2_p1" -> QueryParam(subQuery2Param),
          "q2_q1_p1" -> QueryParam(subQuery2subQueryParam1),
          "q2_q1_p2" -> QueryParam(subQuery2subQueryParam2),
        )
      )

      assert(query.query == expected)
    }
  }

  it should "interpolation with a case class" in {
    val testClass = TestClass("name", 33)

    val query = c"""CREATE (a:Test { $testClass })"""

    val expected = DeferredQuery(
      query = """CREATE (a:Test { name: $p1, age: $p2 })""",
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

    val query = c"""CREATE (a:Test { $testClass }), (b: B { name: $bName })"""

    val expected = DeferredQuery(
      query = """CREATE (a:Test { name: $p1, age: $p2 }), (b: B { name: $p3 })""",
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

    val query = c"""CREATE (a:Test { $testClass }),""" + c"""(b: B { const: "Const", name: $bName })"""

    val expected = DeferredQuery(
      query = """CREATE (a:Test { name: $p1, age: $p2 }), (b: B { const: "Const", name: $p3 })""",
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
      query = "CREATE (u: User { name: $p1, age: $p2 }) -[r:HAS_CAT { friendsFor: $p3 }]->(c:Cat { tag: $p4, age: $p5 }) RETURN r",
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
      query = "CREATE (u: User { name: $p1, age: $p2, extraProperty: $p3 }) RETURN u",
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
  final case class TestClass(name: String, age: Int)

  final case class User(name: String, age: Int)
  final case class Cat(tag: String, age: Int)
  final case class HasCatRelationship(friendsFor: Int)
}
