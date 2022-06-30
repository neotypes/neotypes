package neotypes.generic

import neotypes.{AsyncDriverProvider, QueryArgMapper, CleaningIntegrationSpec, FutureTestkit}
import neotypes.implicits.syntax.all._
import neotypes.mappers.{ParameterMapper, ResultMapper, ValueMapper}

import scala.concurrent.Future

final class AnyValMappersSemiautoSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] {
  import AnyValMappersSemiautoSpec._

  it should "(semi) automatically wrap value classes" in executeAsFuture { d =>
    "RETURN '123'".query[Id].single(d).map { id =>
      assert(id == Id(value = "123"))
    }
  }

  it should "(semi) automatically unwrap value classes" in executeAsFuture { d =>
    for {
      _ <- c"CREATE (: Data { myId: ${Id(value = "135")} })".query[Unit].execute(d)
      result <- "MATCH (d: Data) RETURN d.myId".query[String].single(d)
    } yield {
      assert(result == "135")
    }
  }

  it should "(semi) automatically unwrap and wrap value classes inside other case classes" in executeAsFuture { d =>
    val idValue = "010"
    val id = Id(value = idValue)
    val user = User(id, name = "Luis")

    for {
      _ <- c"CREATE (: User { $user })".query[Unit].execute(d)
      r1 <- "MATCH (u: User) RETURN u.id".query[String].single(d)
      r2 <- "MATCH (u: User) RETURN u.id".query[Id].single(d)
      r3 <- "MATCH (u: User) RETURN u".query[User].single(d)
    } yield {
      assert(r1 == idValue)
      assert(r2 == id)
      assert(r3 == user)
    }
  }

  it should "not work for classes with more than one field" in assertDoesNotCompile("""
    final case class Id2(value1: String, value2: String)
    implicit final val id2ParameterMapper: ParameterMapper[Id2] = semiauto.deriveUnwrappedParameterMapper
    implicit final val id2ValueMapper: ValueMapper[Id2] = semiauto.deriveUnwrappedValueMapper
  """)
}

object AnyValMappersSemiautoSpec {
  final case class Id(value: String) extends AnyVal
  implicit final val idParameterMapper: ParameterMapper[Id] = semiauto.deriveUnwrappedParameterMapper
  implicit final val idValueMapper: ValueMapper[Id] = semiauto.deriveUnwrappedValueMapper
  implicit final val idResultMapper: ResultMapper[Id] = ResultMapper.fromValueMapper

  final case class User(id: Id, name: String)
  implicit final val userArgMapper: QueryArgMapper[User] = semiauto.deriveCaseClassArgMapper
  implicit final val userResultMapper: ResultMapper[User] = semiauto.deriveProductResultMapper
}
