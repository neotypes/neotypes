package neotypes.generic

import neotypes.BaseSynchronousSpec
import neotypes.generic.implicits.deriveCaseClassProductMap
import neotypes.mappers.ResultMapper
import neotypes.model.types._

import scala.collection.immutable.SeqMap

/** Base class for testing the derivation of a ResultMapper instance for case classes. */
final class DeriveCaseClassProductMapSpec extends BaseSynchronousSpec {
  import DeriveCaseClassProductMapSpec._

  behavior of "neotypes.implicits.deriveCaseClassProductMap"

  it should "derive an instance for wrapper case class" in {
    val mapper = ResultMapper.productDerive[WrapperCaseClass]

    val input = NeoMap(properties =
      SeqMap(
        "int" -> Value.Integer(1)
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe WrapperCaseClass(1)
  }

  it should "derive an instance for a case class composed of simple types" in {
    val mapper = ResultMapper.productDerive[SimpleCaseClass]

    val input = NeoMap(properties =
      SeqMap(
        "int" -> Value.Integer(3),
        "str" -> Value.Str("Luis"),
        "bool" -> Value.Bool(true)
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe SimpleCaseClass(
      int = 3,
      str = "Luis",
      bool = true
    )
  }

  it should "derive an instance for a case class composed of complex types" in {
    val mapper = ResultMapper.productDerive[ComplexCaseClass]

    val input = NeoMap(
      properties = SeqMap(
        "opt" -> Value.Integer(5),
        "either" -> Value.Bool(true),
        "list" -> Value.ListValue(List(Value.Integer(0), Value.Integer(10))),
        "map" -> NeoMap(SeqMap("foo" -> Value.Str("bar"), "baz" -> Value.Str("quax")))
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe ComplexCaseClass(
      opt = Some(5),
      either = Right(true),
      list = List(0, 10),
      map = Map("foo" -> "bar", "baz" -> "quax")
    )
  }

  it should "derive an instance for a nested case class" in {
    val mapper = ResultMapper.productDerive[NestedCaseClass]

    val input = NeoMap(
      properties = SeqMap(
        "w" -> NeoMap(properties = SeqMap("int" -> Value.Integer(1))),
        "s" -> NeoMap(
          properties = SeqMap(
            "int" -> Value.Integer(3),
            "str" -> Value.Str("Luis"),
            "bool" -> Value.Bool(true)
          )
        ),
        "c" -> NeoMap(
          properties = SeqMap(
            "opt" -> Value.Integer(5),
            "either" -> Value.Bool(true),
            "list" -> Value.ListValue(List(Value.Integer(0), Value.Integer(10))),
            "map" -> NeoMap(SeqMap("foo" -> Value.Str("bar"), "baz" -> Value.Str("quax")))
          )
        )
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe NestedCaseClass(
      w = WrapperCaseClass(1),
      s = SimpleCaseClass(
        int = 3,
        str = "Luis",
        bool = true
      ),
      c = ComplexCaseClass(
        opt = Some(5),
        either = Right(true),
        list = List(0, 10),
        map = Map("foo" -> "bar", "baz" -> "quax")
      )
    )
  }

  it should "not derive an instance for an untagged HList" in {
    assertDoesNotCompile("import shapeless._; ResultMapper.productDerive[Int :: String :: HNil]")
  }
}

object DeriveCaseClassProductMapSpec {
  final case class WrapperCaseClass(int: Int)
  final case class SimpleCaseClass(int: Int, str: String, bool: Boolean)
  final case class ComplexCaseClass(
    opt: Option[Int],
    either: Either[String, Boolean],
    list: List[Int],
    map: Map[String, String]
  )
  final case class NestedCaseClass(w: WrapperCaseClass, s: SimpleCaseClass, c: ComplexCaseClass)
}
