package neotypes.generic

import neotypes.{QueryArgMapper, QueryArg}
import neotypes.types.QueryParam

import org.scalatest.freespec.AnyFreeSpec

final class QueryArgMapperAutoSpec extends AnyFreeSpec {
  import QueryArgMapperAutoSpec._

  "neotypes.generic.auto._" - {
    import neotypes.generic.auto._

    "should derive an instance of a product (case class)" in {
      val mapper = QueryArgMapper[MyCaseClass]
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
      val mapper = QueryArgMapper[(String, Int)]
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

    "should prioritize an instance from companion object over derived" in {
      val mapper = QueryArgMapper[ObjectScopeQueryArgMapper]
      val input = ObjectScopeQueryArgMapper("const?")

      val expected = QueryArg.CaseClass(Map("const" -> QueryParam("const")))

      assert(mapper.toArg(input) == expected)
    }

    "should not derive an instance of nested classes" in {
      assertCompiles("QueryArgMapper[MyCaseClass]")
      assertDoesNotCompile("QueryArgMapper[(MyCaseClass, MyCaseClass)]")
      assertDoesNotCompile("QueryArgMapper[NestedCaseClass]")
    }

    "should not derive an instance of a HList" in {
      assertDoesNotCompile(
        """
          |import shapeless._
          |QueryArgMapper[String :: Int :: HNil]
          |""".stripMargin
      )
    }
  }
}

object QueryArgMapperAutoSpec {
  final case class MyCaseClass(string: String, int: Int)

  final case class NestedCaseClass(value: String, nested: MyCaseClass)

  final case class ObjectScopeQueryArgMapper(value: String)
  object ObjectScopeQueryArgMapper {
    implicit final val caseClassArgMapper: QueryArgMapper[ObjectScopeQueryArgMapper] =
      new QueryArgMapper[ObjectScopeQueryArgMapper] {
        override def toArg(value: ObjectScopeQueryArgMapper): QueryArg.CaseClass =
          QueryArg.CaseClass(Map("const" -> QueryParam("const")))
      }
  }
}
