package neotypes.generic

import neotypes.model.query.QueryParam
import neotypes.query.{QueryArg, QueryArgMapper}
import neotypes.generic.implicits.deriveCaseClassQueryParams

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Base class for testing the derivation of a QueryArgMapper instance for case classes. */
final class DeriveCaseClassQueryParamSpec extends AnyFlatSpec with Matchers {
  import DeriveCaseClassQueryParamSpec._

  behavior of "neotypes.implicits.deriveCaseClassQueryParams"

  it should "derive an instance for a case class" in {
    val mapper = QueryArgMapper[MyCaseClass]

    val input = MyCaseClass("twelve chars", 12)
    val result = mapper.toArg(input)

    result shouldBe QueryArg.Params(
      List(
        "string" -> QueryParam("twelve chars"),
        "int" -> QueryParam(12)
      )
    )
  }

  it should "not derive an instance for nested classes" in {
    assertDoesNotCompile("QueryArgMapper[(MyCaseClass, MyCaseClass)]")
    assertDoesNotCompile("QueryArgMapper[NestedCaseClass]")
  }

  it should "not derive an instance for an untagged HList" in {
    assertDoesNotCompile("import shapeless._; QueryArgMapper[Int :: String :: HNil]")
  }
}

object DeriveCaseClassQueryParamSpec {
  final case class MyCaseClass(string: String, int: Int)
  final case class NestedCaseClass(value: String, nested: MyCaseClass)
}
