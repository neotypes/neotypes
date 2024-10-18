package neotypes.generic

import neotypes.BaseSynchronousSpec
import neotypes.mappers.ResultMapper
import neotypes.model.types._

import scala.collection.immutable.SeqMap

/** Base class for testing the derivation of a ResultMapper instance for tuples. */
final class DeriveTupleMapSpec extends BaseSynchronousSpec {

  behavior of "neotypes.implicits.deriveTupleMap"

  it should "derive an instance for a tuple composed of simple types" in {
    val mapper = ResultMapper.tuple[Int, String, Boolean]

    val input = NeoMap(properties =
      SeqMap(
        "int" -> Value.Integer(3),
        "str" -> Value.Str("Luis"),
        "bool" -> Value.Bool(true)
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe (3, "Luis", true)
  }

  it should "derive a tuple composed of more complex types" in {
    val mapper =
      ResultMapper.tuple[Option[Int], Either[String, Boolean], List[Int], Map[String, String], Option[Set[Long]]]

    val input = NeoMap(
      properties = SeqMap(
        "opt" -> Value.Integer(5),
        "either" -> Value.Bool(true),
        "list" -> Value.ListValue(List(Value.Integer(0), Value.Integer(10))),
        "map" -> NeoMap(SeqMap("foo" -> Value.Str("bar"), "baz" -> Value.Str("quax"))),
        "none" -> Value.NullValue
      )
    )
    val result = mapper.decode(input)

    result.value shouldBe (Some(5), Right(true), List(0, 10), Map("foo" -> "bar", "baz" -> "quax"), None)
  }
}
