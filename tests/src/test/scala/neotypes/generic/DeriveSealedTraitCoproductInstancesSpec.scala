package neotypes.generic

import neotypes.BaseSynchronousSpec
import neotypes.mappers.ResultMapper
import neotypes.model.types._
import neotypes.generic.implicits.deriveSealedTraitCoproductInstances

/** Base class for testing the derivation of a ResultMapper instance for sealed traits. */
final class DeriveSealedTraitCoproductInstancesSpec extends BaseSynchronousSpec {
  import DeriveSealedTraitCoproductInstancesSpec._

  behavior of "neotypes.implicits.deriveSealedTraitCoproductInstances"

  it should "derive an instance for a simple ADT composed of case classes composed of supported types" in {
    val mapper = ResultMapper.coproductDerive[SimpleADT]

    // First case.
    locally {
      val input = NeoMap(
        properties = Map(
          "type" -> Value.Str("Foo"),
          "int" -> Value.Integer(3),
          "str" -> Value.Str("Luis")
        )
      )
      val result = mapper.decode(input)

      result.value shouldBe SimpleADT.Foo(
        int = 3,
        str = "Luis"
      )
    }

    // Second case.
    locally {
      val input = NeoMap(
        properties = Map(
          "type" -> Value.Str("Bar"),
          "bool" -> Value.Bool(true)
        )
      )
      val result = mapper.decode(input)

      result.value shouldBe SimpleADT.Bar(
        bool = true
      )
    }
  }

  it should "derive an instance for a simple Enum composed of case objects" in {
    val mapper = ResultMapper.coproductDerive[SimpleEnum]

    // First case.
    locally {
      val input = NeoMap(
        properties = Map(
          "type" -> Value.Str("Baz")
        )
      )
      val result = mapper.decode(input)

      result.value shouldBe SimpleEnum.Baz
    }

    // Second case.
    locally {
      val input = NeoMap(
        properties = Map(
          "type" -> Value.Str("Quax")
        )
      )
      val result = mapper.decode(input)

      result.value shouldBe SimpleEnum.Quax
    }
  }

  it should "not derive an instance for an untagged HList" in {
    assertDoesNotCompile("import shapeless._; ResultMapper.coproductDerive[Int :: String :: HNil]")
  }
}

object DeriveSealedTraitCoproductInstancesSpec {
  sealed trait SimpleADT
  object SimpleADT {
    final case class Foo(int: Int, str: String) extends SimpleADT
    object Foo {
      implicit final val FooMapper = ResultMapper.fromFunctionNamed("int", "str")(Foo.apply _)
    }

    final case class Bar(bool: Boolean) extends SimpleADT
    object Bar {
      implicit final val BarMapper = ResultMapper.field(key = "bool", mapper = ResultMapper.boolean).map(Bar.apply)
    }
  }

  sealed trait SimpleEnum
  object SimpleEnum {
    final case object Baz extends SimpleEnum
    final case object Quax extends SimpleEnum
  }
}
