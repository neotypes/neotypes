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
      params = Map("p1" -> QueryParam("John")),
      paramLocations = Seq(22)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
    assert(query.query == expected)
  }

  it should "interpolation with no params" in {
    val query = c"""create (a:Test {name: "test"})"""

    val expected = DeferredQuery(
      query = """create (a:Test {name: "test"})""",
      params = Map.empty,
      paramLocations = Seq.empty
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
      params = Map("p1" -> QueryParam("John"), "p2" -> QueryParam(1980)),
      paramLocations = Seq(33, 22)
    )
    val expected2 = DeferredQuery(
      query  = """create (a:Test {name: "John", born: $p1})""",
      params = Map("p1" -> QueryParam(1980)),
      paramLocations = Seq(36)
    )
    val expected3 = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: 1980})""",
      params = Map("p1" -> QueryParam("John")),
      paramLocations = Seq(22)
    )

    assert(query1.query.paramLocations.forall(query1.query.query.charAt(_) == '$'))
    assert(query2.query.paramLocations.forall(query2.query.query.charAt(_) == '$'))
    assert(query3.query.paramLocations.forall(query3.query.query.charAt(_) == '$'))
    assert(query1.query == expected1)
    assert(query2.query == expected2)
    assert(query3.query == expected3)
  }

  it should "interpolate DeferredQuery instances" in {
    // Single subquery
    locally {
      val subQuery = c"""user.id = "1""""
      val query = c"""MATCH (user:User) WHERE $subQuery RETURN user"""
      val expected = DeferredQuery(
        query = """MATCH (user:User) WHERE user.id = "1" RETURN user""",
        params = Map.empty,
        paramLocations = Seq.empty
      )
      assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
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
        params = Map("q1_p1" -> QueryParam(subQueryParam1), "q1_p2" -> QueryParam(subQueryParam2)),
        paramLocations = Seq(34, 45)
      )
      assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
      assert(query.query == expected)
    }

    // Multiple nested subqueries with parameters
    locally {
      val subQuery1subQueryParam1 = 1
      val subQuery1subQueryParam2 = false
      val subQuery1subQuery = c"user.id = $subQuery1subQueryParam1 AND $subQuery1subQueryParam2"

      val subQuery1Param = "foo"
      val subQuery1 = c"user.name = $subQuery1Param AND $subQuery1subQuery"

      val subQuery2subQueryParam1 = 2
      val subQuery2subQueryParam2 = true
      val subQuery2subQuery = c"user.id = $subQuery2subQueryParam1 AND $subQuery2subQueryParam2"

      val subQuery2Param = "bar"
      val subQuery2 = c"user.name = $subQuery2Param AND $subQuery2subQuery"

      val query = c"MATCH (user:User) WHERE ($subQuery1) OR ($subQuery2)"
      val expected = DeferredQuery(
        query =
          "MATCH (user:User) " +
            "WHERE " +
            "( user.name = $q1_p1 AND user.id = $q1_q1_p1 AND $q1_q1_p2 ) OR " +
            "( user.name = $q2_p1 AND user.id = $q2_q1_p1 AND $q2_q1_p2 )",
        params = Map(
          "q1_p1" -> QueryParam("foo"),
          "q1_q1_p1" -> QueryParam(1),
          "q1_q1_p2" -> QueryParam(false),
          "q2_p1" -> QueryParam("bar"),
          "q2_q1_p1" -> QueryParam(2),
          "q2_q1_p2" -> QueryParam(true),
        ),
        paramLocations = Seq(38, 59, 73, 102, 123, 137)
      )
      assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
      assert(query.query == expected)
    }
  }

  it should "concat DeferredQueryBuilder with String" in {
    val name = "John"
    val query = c"""create (a:Test {name: $name,""" + "born: 1980})"

    val expected = DeferredQuery(
      query  = """create (a:Test {name: $p1, born: 1980})""",
      params = Map("p1" -> QueryParam("John")),
      paramLocations = Seq(22)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
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
      params = Map("p1" -> QueryParam("John"), "p2" -> QueryParam("Smith"), "p3" -> QueryParam(1980)),
      paramLocations = Seq(74, 43, 28)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
    assert(query.query == expected)
  }

  it should "interpolation with a case class" in {
    val testClass = TestClass("name", 33)

    val query = c"""create (a:Test { $testClass })"""

    val expected = DeferredQuery(
      query = """create (a:Test { name: $p1, age: $p2 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age)
      ),
      paramLocations = Seq(33, 23)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
    assert(query.query == expected)
  }

  it should "interpolation with a case class and extra args" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val query = c"""create (a:Test { $testClass }), (b: B { name: $bName })"""

    val expected = DeferredQuery(
      query = """create (a:Test { name: $p1, age: $p2 }), (b: B { name: $p3 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age),
        "p3" -> QueryParam(bName)
      ),
      paramLocations = Seq(55, 33, 23)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
    assert(query.query == expected)
  }

  it should "interpolation with a case class and extra args (concat queries)" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val query = c"""create (a:Test { $testClass }),""" + c"""(b: B { const: "Const", name: $bName })"""

    val expected = DeferredQuery(
      query = """create (a:Test { name: $p1, age: $p2 }), (b: B { const: "Const", name: $p3 })""",
      params = Map(
        "p1" -> QueryParam(testClass.name),
        "p2" -> QueryParam(testClass.age),
        "p3" -> QueryParam(bName)
      ),
      paramLocations = Seq(71, 33, 23)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
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
      ),
      paramLocations = Seq(99, 89, 67, 34, 24)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
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
      ),
      paramLocations = Seq(54, 34, 24)
    )

    assert(query.query.paramLocations.forall(query.query.query.charAt(_) == '$'))
    assert(query.query == expected)
  }
}

object CypherQueryInterpolationSpec {

  case class TestClass(name: String, age: Int)

  case class User(name: String, age: Int)
  case class Cat(tag: String, age: Int)
  case class HasCatRelationship(friendsFor: Int)

}
