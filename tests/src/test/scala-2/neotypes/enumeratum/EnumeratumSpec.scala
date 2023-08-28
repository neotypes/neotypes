package neotypes.enumeratum

import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry}
import neotypes._
import neotypes.internal.syntax.async._
import neotypes.mappers.ResultMapper
import neotypes.model.exceptions.{IncoercibleException, KeyMapperException}
import neotypes.syntax.all._

/** Base class for testing the use of the library with Enumeratum. */
sealed trait BaseEnumeratumSpec[F[_]] extends CleaningIntegrationSpec[F] { self: DriverProvider[F] =>
  import BaseEnumeratumSpec._

  behavior of s"${driverName} with Enumeratum enums"

  it should "work with a simple Enum" in {
    forAll(SimpleEnum.values) { enumValue =>
      executeAsFuture { driver =>
        for {
          _ <- c"CREATE (: Data { name: ${enumValue.entryName}, value: ${enumValue} })".execute.void(driver)
          r <- c"MATCH (data: Data { name: ${enumValue.entryName} }) RETURN data.value"
            .query(SimpleEnum.resultMapper)
            .single(driver)
        } yield {
          r shouldBe enumValue
        }
      }
    }
  }

  it should "fail if retrieving a non-valid value for a simple Enum" in {
    recoverToSucceededIf[KeyMapperException] {
      executeAsFuture { driver =>
        "RETURN 'Quax'".query(SimpleEnum.resultMapper).single(driver)
      }
    }
  }

  it should "work with a value (int) Enum" in {
    forAll(ValueEnum.values) { enumValue =>
      executeAsFuture { driver =>
        for {
          _ <- c"CREATE (: Data { name: ${enumValue.name}, value: ${enumValue} })".execute.void(driver)
          r <- c"MATCH (data: Data { name: ${enumValue.name}}) RETURN data.value"
            .query(ValueEnum.resultMapper)
            .single(driver)
        } yield {
          r shouldBe enumValue
        }
      }
    }
  }

  it should "fail if retrieving a non-valid value for a value (int) Enum" in {
    recoverToSucceededIf[IncoercibleException] {
      executeAsFuture { driver =>
        "RETURN 10".query(ValueEnum.resultMapper).single(driver)
      }
    }
  }

  it should "work with a key Enum" in executeAsFuture { driver =>
    val data = Map(
      KeyEnum.Key1 -> 0,
      KeyEnum.Key2 -> 10,
      KeyEnum.Key3 -> 100
    )

    for {
      _ <- c"CREATE (: Data ${data})".execute.void(driver)
      r <- "MATCH (data: Data) RETURN data".query(KeyEnum.mapMapper).single(driver)
    } yield {
      r shouldBe data
    }
  }

  it should "fail if retrieving a non-valid value for a key Enum" in {
    recoverToSucceededIf[KeyMapperException] {
      executeAsFuture { driver =>
        "RETURN { quax : 7 }".query(KeyEnum.mapMapper).single(driver)
      }
    }
  }

  it should "work with a value (string) key Enum" in executeAsFuture { driver =>
    val data = Map(
      KeyStringEnum.KeyA -> 0,
      KeyStringEnum.KeyB -> 10,
      KeyStringEnum.KeyC -> 100
    )

    for {
      _ <- c"CREATE (: Data ${data})".execute.void(driver)
      r <- "MATCH (data: Data) RETURN data".query(KeyStringEnum.mapMapper).single(driver)
    } yield {
      r shouldBe data
    }
  }

  it should "fail if retrieving a non-valid value for a value (string) key Enum" in {
    recoverToSucceededIf[KeyMapperException] {
      executeAsFuture { driver =>
        "RETURN { quax : 7 }".query(KeyStringEnum.mapMapper).single(driver)
      }
    }
  }
}

object BaseEnumeratumSpec {
  sealed trait SimpleEnum extends EnumEntry with Product with Serializable
  object SimpleEnum extends Enum[SimpleEnum] with NeotypesEnum[SimpleEnum] {
    final case object Foo extends SimpleEnum
    final case object Bar extends SimpleEnum
    final case object Baz extends SimpleEnum

    val values = findValues
  }

  sealed abstract class ValueEnum(val value: Int, val name: String) extends IntEnumEntry with Product with Serializable
  object ValueEnum extends IntEnum[ValueEnum] with NeotypesIntEnum[ValueEnum] {
    final case object A extends ValueEnum(value = 1, name = "A")
    final case object B extends ValueEnum(value = 3, name = "B")
    final case object C extends ValueEnum(value = 5, name = "C")

    val values = findValues
  }

  sealed trait KeyEnum extends EnumEntry with Product with Serializable
  object KeyEnum extends Enum[KeyEnum] with NeotypesKeyEnum[KeyEnum] { self =>
    final case object Key1 extends KeyEnum
    final case object Key2 extends KeyEnum
    final case object Key3 extends KeyEnum

    val values = findValues

    val mapMapper = ResultMapper.neoMap(
      keyMapper = self.keyMapper,
      valueMapper = ResultMapper.int
    )
  }

  sealed abstract class KeyStringEnum(val value: String) extends StringEnumEntry with Product with Serializable
  object KeyStringEnum extends StringEnum[KeyStringEnum] with NeotypesStringEnum[KeyStringEnum] { self =>
    final case object KeyA extends KeyStringEnum(value = "keyA")
    final case object KeyB extends KeyStringEnum(value = "keyB")
    final case object KeyC extends KeyStringEnum(value = "keyC")

    val values = findValues

    val mapMapper = ResultMapper.neoMap(
      keyMapper = self.keyMapper,
      valueMapper = ResultMapper.int
    )
  }
}

final class AsyncEnumeratumSpec[F[_]](
  testkit: AsyncTestkit[F]
) extends AsyncDriverProvider(testkit)
    with BaseEnumeratumSpec[F]

final class StreamEnumeratumSpec[S[_], F[_]](
  testkit: StreamTestkit[S, F]
) extends StreamDriverProvider(testkit)
    with BaseEnumeratumSpec[F]
