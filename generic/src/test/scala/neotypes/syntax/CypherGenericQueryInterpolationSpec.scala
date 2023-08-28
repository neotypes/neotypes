package neotypes.syntax

import neotypes.BaseSynchronousSpec
import neotypes.generic.implicits._
import neotypes.model.query.QueryParam
import neotypes.syntax.cypher._

final class CypherGenericQueryInterpolationSpec extends BaseSynchronousSpec {
  import CypherGenericQueryInterpolationSpec._

  behavior of s"Neotypes Cypher String interpolator plus generic capabilities"

  it should "interpolation with a case class" in {
    val testClass = TestClass("name", 33)

    val (query, params, _) = c"CREATE (a: Test { $testClass })".build()

    query shouldBe "CREATE (a: Test { name: $p1, age: $p2 })"
    params shouldBe Map(
      "p1" -> QueryParam(testClass.name),
      "p2" -> QueryParam(testClass.age)
    )
  }

  it should "interpolation with a case class and extra args" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val (query, params, _) = c"CREATE (a: Test { $testClass }), (b: B { name: $bName })".build()

    query shouldBe "CREATE (a: Test { name: $p1, age: $p2 }), (b: B { name: $p3 })"
    params shouldBe Map(
      "p1" -> QueryParam(testClass.name),
      "p2" -> QueryParam(testClass.age),
      "p3" -> QueryParam(bName)
    )
  }

  it should "interpolation with a case class and extra args (concat queries)" in {
    val testClass = TestClass("name", 33)
    val bName = "b-name"

    val (query, params, _) = (c"CREATE (a: Test { $testClass })," + c"(b: B { const: 'Const', name: $bName })").build()

    query shouldBe "CREATE (a: Test { name: $p1, age: $p2 }), (b: B { const: 'Const', name: $p3 })"
    params shouldBe Map(
      "p1" -> QueryParam(testClass.name),
      "p2" -> QueryParam(testClass.age),
      "p3" -> QueryParam(bName)
    )
  }

  it should "interpolation with case classes in a relationship" in {
    val user = User("Joan", 20)
    val cat = Cat("Waffles", 3)
    val relationship = HasCatRelationship(2)

    val (query, params, _) =
      c"CREATE (u: User { $user })-[r: HAS_CAT { $relationship }]->(c: Cat { $cat }) RETURN r".build()

    query shouldBe "CREATE (u: User { name: $p1, age: $p2 })-[r: HAS_CAT { friendsFor: $p3 }]->(c: Cat { tag: $p4, age: $p5 }) RETURN r"
    params shouldBe Map(
      "p1" -> QueryParam(user.name),
      "p2" -> QueryParam(user.age),
      "p3" -> QueryParam(relationship.friendsFor),
      "p4" -> QueryParam(cat.tag),
      "p5" -> QueryParam(cat.age)
    )
  }

  it should "interpolation with a case classes and an extra property" in {
    val user = User("Joan", 20)
    val extraProp = 123

    val (query, params, _) = c"CREATE (u: User { $user, extraProperty: $extraProp }) RETURN u".build()

    query shouldBe "CREATE (u: User { name: $p1, age: $p2, extraProperty: $p3 }) RETURN u"
    params shouldBe Map(
      "p1" -> QueryParam(user.name),
      "p2" -> QueryParam(user.age),
      "p3" -> QueryParam(extraProp)
    )
  }
}

object CypherGenericQueryInterpolationSpec {
  final case class TestClass(name: String, age: Int)

  final case class User(name: String, age: Int)
  final case class Cat(tag: String, age: Int)
  final case class HasCatRelationship(friendsFor: Int)
}
