package neotypes.generic

import neotypes.{QueryArgMapper, QueryArg}
import neotypes.types.QueryParam

import org.scalatest.freespec.AnyFreeSpec

final class QueryArgMapperSemiautoSpec extends AnyFreeSpec {
  import QueryArgMapperSemiautoSpec._

  "neotypes.generic.semiauto._" - {
    import neotypes.generic.semiauto._

    "should derive an instance of a product (case class)" in {
      val mapper: QueryArgMapper[MyCaseClass] = deriveCaseClassArgMapper
      val input = MyCaseClass("twelve chars", 12)
      val result = mapper.toArg(input)

      val expected = QueryArg.CaseClass(
        Map(
          "string" -> QueryParam("twelve chars"),
          "int" -> QueryParam(12)
        )
      )

      assert(result == expected)
    }

    "should derive an instance of a product (tuple)" in {
      val mapper: QueryArgMapper[(String, Int)] = deriveCaseClassArgMapper
      val input = ("twelve chars", 12)
      val result = mapper.toArg(input)

      val expected = QueryArg.CaseClass(
        Map(
          "_1" -> QueryParam("twelve chars"),
          "_2" -> QueryParam(12)
        )
      )

      assert(result == expected)
    }

    "should not derive an instance of nested classes" in {
      assertCompiles("deriveCaseClassArgMapper[MyCaseClass]")
      assertDoesNotCompile("deriveQueryArgMapper[(MyCaseClass, MyCaseClass)]")
      assertDoesNotCompile("deriveQueryArgMapper[NestedCaseClass]")
    }

    "should not derive an instance of a HList" in {
      assertDoesNotCompile(
        """
          |import shapeless._
          |deriveQueryArgMapper[String :: Int :: HNil]
          |""".stripMargin
      )
    }
  }
}

object QueryArgMapperSemiautoSpec {
  final case class MyCaseClass(string: String, int: Int)
  final case class NestedCaseClass(value: String, nested: MyCaseClass)
}
