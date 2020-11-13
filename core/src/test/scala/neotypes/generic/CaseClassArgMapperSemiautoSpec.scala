package neotypes.generic

import neotypes.{CaseClassArgMapper, QueryArg}
import neotypes.types.QueryParam

import org.scalatest.freespec.AnyFreeSpec

final class CaseClassArgMapperSemiautoSpec extends AnyFreeSpec {

  import CaseClassArgMapperSemiautoSpec._

  "neotypes.generic.semiauto._" - {

    import neotypes.generic.semiauto._

    "should derive an instance of a product (case class)" in {
      val mapper: CaseClassArgMapper[MyCaseClass] = deriveCaseClassArgMapper
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
      val mapper: CaseClassArgMapper[(String, Int)] = deriveCaseClassArgMapper
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
      assertDoesNotCompile("deriveCaseClassArgMapper[(MyCaseClass, MyCaseClass)]")
      assertDoesNotCompile("deriveCaseClassArgMapper[NestedCaseClass]")
    }

    "should not derive an instance of a HList" in {
      assertDoesNotCompile(
        """
          |import shapeless._
          |deriveCaseClassArgMapper[String :: Int :: HNil]
          |""".stripMargin
      )
    }

  }

}

object CaseClassArgMapperSemiautoSpec {

  final case class MyCaseClass(string: String, int: Int)

  final case class NestedCaseClass(value: String, nested: MyCaseClass)

}

