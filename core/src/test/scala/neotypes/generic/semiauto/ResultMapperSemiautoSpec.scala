package neotypes.generic.semiauto

import java.{util => ju}

import neotypes.implicits.mappers.results._
import neotypes.mappers.ResultMapper
import org.neo4j.driver.internal.InternalNode
import org.neo4j.driver.internal.value.{IntegerValue, NodeValue, StringValue}
import org.scalatest.freespec.AnyFreeSpec
import shapeless.{::, HNil}

final class ResultMapperSemiautoSpec extends AnyFreeSpec {

  import ResultMapperSemiautoSpec.{MyCaseClass, ObjectScopeResultMapper}

  "neotypes.generic.semiauto.all._" - {

    import neotypes.generic.semiauto.all._

    "should derive an instance of a HList" in {
      val mapper: ResultMapper[String :: Int :: HNil] = deriveHListResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right("twelve chars" :: 12 :: HNil))
    }

    "should derive an instance of a product (case class)" in {
      val mapper: ResultMapper[MyCaseClass] = deriveProductResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(MyCaseClass("twelve chars", 12)))
    }

    "should derive an instance of a product (tuple)" in {
      val mapper: ResultMapper[(String, Int)] = deriveProductResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(("twelve chars", 12)))
    }

    "should prioritize a derived instance over companion object" in {
      val const = ObjectScopeResultMapper.const

      val tupleMapper: ResultMapper[(ObjectScopeResultMapper, ObjectScopeResultMapper)] = deriveProductResultMapper

      val node = new InternalNode(
        1,
        ju.Collections.singletonList("Node"),
        ju.Collections.singletonMap("value", new StringValue("1"))
      )

      val input = List(
        ("v1", new NodeValue(node)),
        ("v2", new NodeValue(node))
      )

      assert(ResultMapper[ObjectScopeResultMapper].to(input, None) == Right(const))
      assert(tupleMapper.to(input, None) == Right((const, const)))
    }

  }

  "neotypes.generic.semiauto.product._" - {

    import neotypes.generic.semiauto.product._

    "should not derive an instance of a HList" in {
      assertCompiles("val m: ResultMapper[MyCaseClass] = deriveProductResultMapper")
      assertDoesNotCompile("val m: ResultMapper[String :: Int :: HNil] = deriveHListResultMapper")
    }

    "should derive an instance of a product (case class)" in {
      val mapper: ResultMapper[MyCaseClass] = deriveProductResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(MyCaseClass("twelve chars", 12)))
    }

    "should derive an instance of a product (tuple)" in {
      val mapper: ResultMapper[(String, Int)] = deriveProductResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(("twelve chars", 12)))
    }

  }

  "neotypes.generic.semiauto.hlist._" - {

    import neotypes.generic.semiauto.hlist._

    "should derive an instance of a HList" in {
      val mapper: ResultMapper[String :: Int :: HNil] = deriveHListResultMapper

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right("twelve chars" :: 12 :: HNil))
    }

    "should not derive an instance of a product (case class)" in {
      assertCompiles("val m: ResultMapper[String :: Int :: HNil] = deriveHListResultMapper")
      assertDoesNotCompile("val m: ResultMapper[MyCaseClass] = deriveProductResultMapper")
    }

    "should not derive an instance of a product (tuple)" in {
      assertCompiles("val m: ResultMapper[String :: Int :: HNil] = deriveHListResultMapper")
      assertDoesNotCompile("val m: ResultMapper[(String, Int)] = deriveProductResultMapper")
    }

  }

}

object ResultMapperSemiautoSpec {

  final case class MyCaseClass(string: String, int: Int)

  final case class ObjectScopeResultMapper(value: String)

  object ObjectScopeResultMapper {
    val const = ObjectScopeResultMapper("const")

    implicit val resultMapper: ResultMapper[ObjectScopeResultMapper] =
      ResultMapper.const(const)
  }

}
