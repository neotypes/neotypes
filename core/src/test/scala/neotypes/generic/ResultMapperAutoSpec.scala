package neotypes.generic

import java.{util => ju}

import neotypes.mappers.ResultMapper

import org.neo4j.driver.internal.InternalNode
import org.neo4j.driver.internal.value.{IntegerValue, NodeValue, StringValue}
import org.scalatest.freespec.AnyFreeSpec
import shapeless.{::, HNil}

final class ResultMapperAutoSpec extends AnyFreeSpec {

  import ResultMapperAutoSpec.{MyCaseClass, ObjectScopeResultMapper}

  "neotypes.generic.auto._" - {

    import neotypes.generic.auto._

    "should derive an instance of a HList" in {
      val mapper = ResultMapper[String :: Int :: HNil]

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right("twelve chars" :: 12 :: HNil))
    }

    "should derive an instance of a product (case class)" in {
      val mapper = ResultMapper[MyCaseClass]

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(MyCaseClass("twelve chars", 12)))
    }

    "should derive an instance of a product (tuple)" in {
      val mapper = ResultMapper[(String, Int)]

      val input = List(
        ("string", new StringValue("twelve chars")),
        ("int", new IntegerValue(12))
      )

      val result = mapper.to(input, None)
      assert(result == Right(("twelve chars", 12)))
    }

    "should prioritize an instance from companion object over derived" in {
      val const = ObjectScopeResultMapper.const

      val tupleMapper: ResultMapper[(ObjectScopeResultMapper, ObjectScopeResultMapper)] =
        implicitly

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

}

object ResultMapperAutoSpec {

  final case class MyCaseClass(string: String, int: Int)

  final case class ObjectScopeResultMapper(value: String)

  object ObjectScopeResultMapper {
    val const = ObjectScopeResultMapper("const")

    implicit val resultMapper: ResultMapper[ObjectScopeResultMapper] =
      ResultMapper.const(const)
  }

}
