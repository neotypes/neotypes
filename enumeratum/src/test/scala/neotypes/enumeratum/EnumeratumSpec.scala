package neotypes.enumeratum

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.{IncoercibleException, KeyMapperException}
import neotypes.syntax.all._

import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry}
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

final class EnumeratumSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] with Matchers with Inspectors {
  import EnumeratumSpec._

  behavior of s"${driverName} with Enumeratum enums"

  it should "work with a simple Enums" in executeAsFuture { driver =>
    val mapper = SimpleEnum.resultMapper

    // Success.
    forAll(SimpleEnum.values) { enumValue =>
      for {
        _ <- c"CREATE (: Data { name: ${enumValue.entryName}, value: ${enumValue} })".execute.void(driver)
        r <- c"MATCH (data: Data { name: ${enumValue.entryName} }) RETURN data.value".query(mapper).single(driver)
      } yield {
        r shouldBe enumValue
      }
    }

    // Fail if retrieving a non-valid value.
    recoverToSucceededIf[KeyMapperException] {
      "RETURN 'Quax'".query(mapper).single(driver)
    }
  }

  it should "work with a value Enums (int)" in executeAsFuture { driver =>
    val mapper = ValueEnum.resultMapper

    // Success.
    forAll(ValueEnum.values) { enumValue =>
      for {
        _ <- c"CREATE (: Data { name: ${enumValue.name}, value: ${enumValue} })".execute.void(driver)
        r <- c"MATCH (data: Data { name: ${enumValue.name}}) RETURN data.value".query(mapper).single(driver)
      } yield {
        r shouldBe enumValue
      }
    }

    // Fail if retrieving a non-valid value.
    recoverToSucceededIf[IncoercibleException] {
      "RETURN 10".query(mapper).single(driver)
    }
  }

  it should "work with key Enums" in executeAsFuture { driver =>
    val data = Map(
      KeyEnum.Key1 -> 0,
      KeyEnum.Key2 -> 10,
      KeyEnum.Key3 -> 100
    )
    val mapper = ResultMapper.neoMap(
      keyMapper = KeyEnum.keyMapper,
      valueMapper = ResultMapper.int
    )

    // Success.
    for {
      _ <- c"CREATE (: Data ${data})".execute.void(driver)
      r <- "MATCH (data: Data) RETURN data".query(mapper).single(driver)
    } yield {
      r shouldBe data
    }

    // Fail if retrieving a non-valid value.
    recoverToSucceededIf[KeyMapperException] {
      "RETURN { quax : 7 }".query(mapper).single(driver)
    }
  }

  it should "work with value Enums (string) as keys" in executeAsFuture { driver =>
    val data = Map(
      KeyStringEnum.KeyA -> 0,
      KeyStringEnum.KeyB -> 10,
      KeyStringEnum.KeyC -> 100
    )
    val mapper = ResultMapper.neoMap(
      keyMapper = KeyStringEnum.keyMapper,
      valueMapper = ResultMapper.int
    )

    // Success.
    for {
      _ <- c"CREATE (: Data ${data})".execute.void(driver)
      r <- "MATCH (data: Data) RETURN data".query(mapper).single(driver)
    } yield {
      r shouldBe data
    }

    // Fail if retrieving a non-valid value.
    recoverToSucceededIf[KeyMapperException] {
      "RETURN { quax : 7 }".query(mapper).single(driver)
    }
  }
}

object EnumeratumSpec {
  sealed trait SimpleEnum extends EnumEntry with Product with Serializable
  object SimpleEnum extends Enum[SimpleEnum] with NeotypesEnum[SimpleEnum] {
    final case object Foo extends SimpleEnum
    final case object Bar extends SimpleEnum
    final case object Baz extends SimpleEnum

    val values = findValues
  }

  sealed abstract class ValueEnum (val value: Int, val name: String) extends IntEnumEntry with Product with Serializable
  object ValueEnum extends IntEnum[ValueEnum] with NeotypesIntEnum[ValueEnum] {
    final case object A extends ValueEnum(value = 1, name = "A")
    final case object B extends ValueEnum(value = 3, name = "B")
    final case object C extends ValueEnum(value = 5, name = "C")

    val values = findValues
  }

  sealed trait KeyEnum extends EnumEntry with Product with Serializable
  object KeyEnum extends Enum[KeyEnum] with NeotypesKeyEnum[KeyEnum] {
    final case object Key1 extends KeyEnum
    final case object Key2 extends KeyEnum
    final case object Key3 extends KeyEnum

    val values = findValues
  }

  sealed abstract class KeyStringEnum (val value: String) extends StringEnumEntry with Product with Serializable
  object KeyStringEnum extends StringEnum[KeyStringEnum] with NeotypesStringEnum[KeyStringEnum] {
    final case object KeyA extends KeyStringEnum(value = "keyA")
    final case object KeyB extends KeyStringEnum(value = "keyB")
    final case object KeyC extends KeyStringEnum(value = "keyC")

    val values = findValues
  }
}
