package neotypes.query

import neotypes.DeferredQuery
import neotypes.generic.auto._
import neotypes.types.QueryParam
import org.scalatest.freespec.AnyFreeSpec

class CreateQuerySpec extends AnyFreeSpec {
  import CreateQuerySpec._

  "CreateQuery" - {

    "use a default instance (Unit result)" in {
      val user = User("Xavier", 20)
      val query = CreateQuery[User].query[Unit](user)

      val expected = DeferredQuery(
        query = "CREATE (u: User {  name: $p1, age: $p2 }) ",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age)
        )
      )

      assert(query == expected)
    }

    "use a default instance (Entity result)" in {
      val user = User("Xavier", 20)
      val query = CreateQuery[User].query[User](user)

      val expected = DeferredQuery(
        query = "CREATE (u: User {  name: $p1, age: $p2 }) RETURN u",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age)
        )
      )

      assert(query == expected)
    }

    "use custom parameters" in {
      val user = User("Xavier", 20)
      val query = CreateQuery[User]
        .withNodeName("UserNode")
        .withParamName("user")
        .query[User](user)

      val expected = DeferredQuery(
        query = "CREATE (user: UserNode {  name: $p1, age: $p2 }) RETURN user",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age)
        )
      )

      assert(query == expected)
    }

    "use a default instance (invalid query output)" in {
      assertCompiles("""CreateQuery[User].query[User](User("Xavier", 20))""".stripMargin)
      assertCompiles("""CreateQuery[User].query[Unit](User("Xavier", 20))""".stripMargin)
      assertDoesNotCompile("""CreateQuery[User].query[AnnotatedUser](User("Xavier", 20))""".stripMargin)
    }

    "use an annotated instance (Unit result)" in {
      val user = AnnotatedUser("Xavier", 20)
      val query = CreateQuery[AnnotatedUser].query[Unit](user)

      val expected = DeferredQuery(
        query = "CREATE (user: User {  name: $p1, age: $p2 }) ",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age)
        )
      )

      assert(query == expected)
    }

    "use an annotated instance (Entity result)" in {
      val user = AnnotatedUser("Xavier", 20)
      val query = CreateQuery[AnnotatedUser].query[AnnotatedUser](user)

      val expected = DeferredQuery(
        query = "CREATE (user: User {  name: $p1, age: $p2 }) RETURN user",
        params = Map(
          "p1" -> QueryParam(user.name),
          "p2" -> QueryParam(user.age)
        )
      )

      assert(query == expected)
    }

    "use an annotated instance (invalid query output)" in {
      assertCompiles("CreateQuery.mat[User]")
      assertCompiles("CreateQuery.annotated[AnnotatedUser]")
      assertDoesNotCompile("CreateQuery.annotated[User]")
    }
  }

}

object CreateQuerySpec {

  final case class User(name: String, age: Int)

  @CreateQuery.settings(nodeName = "User", paramName = "user")
  final case class AnnotatedUser(name: String, age: Int)

  object AnnotatedUser {
    implicit val userCreateQuery: CreateQuery[AnnotatedUser] = CreateQuery.annotated
  }

}
