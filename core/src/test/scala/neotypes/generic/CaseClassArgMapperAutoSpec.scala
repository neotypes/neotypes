package neotypes.generic

import neotypes.{CaseClassArgMapper, QueryArg}
import neotypes.types.QueryParam

import org.scalatest.freespec.AnyFreeSpec

final class CaseClassArgMapperAutoSpec extends AnyFreeSpec {

  import CaseClassArgMapperAutoSpec._

  "neotypes.generic.auto._" - {

    import neotypes.generic.auto._

    "should derive an instance of a product (case class)" in {
      val mapper = CaseClassArgMapper[MyCaseClass]
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
      val mapper = CaseClassArgMapper[(String, Int)]
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
      val mapper = CaseClassArgMapper[ObjectScopeCaseClassArgMapper]
      val input = ObjectScopeCaseClassArgMapper("const?")

      val expected = QueryArg.CaseClass(Map("const" -> QueryParam("const")))

      assert(mapper.toArg(input) == expected)
    }

    "should not derive an instance of nested classes" in {
      assertCompiles("CaseClassArgMapper[MyCaseClass]")
      assertDoesNotCompile("CaseClassArgMapper[(MyCaseClass, MyCaseClass)]")
      assertDoesNotCompile("CaseClassArgMapper[NestedCaseClass]")
    }

    "should not derive an instance of a HList" in {
      assertDoesNotCompile(
        """
          |import shapeless._
          |CaseClassArgMapper[String :: Int :: HNil]
          |""".stripMargin
      )
    }

  }

}

object CaseClassArgMapperAutoSpec {

  final case class MyCaseClass(string: String, int: Int)

  final case class NestedCaseClass(value: String, nested: MyCaseClass)

  final case class ObjectScopeCaseClassArgMapper(value: String)

  object ObjectScopeCaseClassArgMapper {
    implicit val caseClassArgMapper: CaseClassArgMapper[ObjectScopeCaseClassArgMapper] =
      new CaseClassArgMapper[ObjectScopeCaseClassArgMapper] {
        override def toArg(value: ObjectScopeCaseClassArgMapper): QueryArg.CaseClass =
          QueryArg.CaseClass(Map("const" -> QueryParam("const")))
      }
  }

}
