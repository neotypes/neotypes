package neotypes.enumeratum

import neotypes.{AsyncDriverProvider, CleaningIntegrationSpec, FutureTestkit}
import neotypes.exceptions.IncoercibleException
import neotypes.generic.semiauto.deriveProductResultMapper
import neotypes.implicits.syntax.all._

import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{IntEnum, IntEnumEntry, StringEnum, StringEnumEntry}

import org.scalatest.Inspectors
import scala.concurrent.Future

final class EnumeratumSpec extends AsyncDriverProvider(FutureTestkit) with CleaningIntegrationSpec[Future] with Inspectors {
  import EnumeratumSpec._

  it should "work with a simple Enums" in {
    forAll(SimpleEnum.values) { enumValue =>
      executeAsFuture { d =>
        for {
          _ <- c"CREATE (: Data { name: ${enumValue.entryName}, value: ${enumValue} })".query[Unit].execute(d)
          r1 <- c"MATCH (data: Data { name: ${enumValue.entryName} }) RETURN data.value".query[String].single(d)
          r2 <- c"MATCH (data: Data { name: ${enumValue.entryName} }) RETURN data.value".query[SimpleEnum].single(d)
          r3 <- c"MATCH (data: Data { name: ${enumValue.entryName} }) RETURN data".query[DataWithSimpleEnum].single(d)
        } yield {
          assert(r1 == enumValue.entryName)
          assert(r2 == enumValue)
          assert(r3 == DataWithSimpleEnum(name = enumValue.entryName, value = enumValue))
        }
      }
    }
  }

  it should "fail if retrieving a non-valid value as simple Enum" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      "RETURN 'Quax'".query[SimpleEnum].single(d)
    }
  }

  it should "work with a value Enums" in {
    forAll(ValueEnum.values) { enumValue =>
      executeAsFuture { d =>
        for {
          _ <- c"CREATE (: Data { name: ${enumValue.name}, value: ${enumValue} })".query[Unit].execute(d)
          r1 <- c"MATCH (data: Data { name: ${enumValue.name}}) RETURN data.value".query[Int].single(d)
          r2 <- c"MATCH (data: Data { name: ${enumValue.name}}) RETURN data.value".query[ValueEnum].single(d)
          r3 <- c"MATCH (data: Data { name: ${enumValue.name}}) RETURN data".query[DataWithValueEnum].single(d)
        } yield {
          assert(r1 == enumValue.value)
          assert(r2 == enumValue)
          assert(r3 == DataWithValueEnum(name = enumValue.name, value = enumValue))
        }
      }
    }
  }

  it should "fail if retrieving a non-valid value as value Enum" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      "RETURN 10".query[ValueEnum].single(d)
    }
  }

  it should "use a key Enum as keys of the properties of a node" in executeAsFuture { d =>
    val data = Map(
      KeyEnum.Key1 -> 0,
      KeyEnum.Key2 -> 10,
      KeyEnum.Key3 -> 100
    )

    for {
      _ <- c"CREATE (: Data ${data})".query[Unit].execute(d)
      r <- "MATCH (data: Data) RETURN data".query[Map[KeyEnum, Int]].single(d)
    } yield {
      assert(r == data)
    }
  }

  it should "fail if retrieving a non-valid value as a key Enum" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      "RETURN { quax : 7 }".query[Map[KeyEnum, Int]].single(d)
    }
  }

  it should "use a string Enum as keys of the properties of a node" in executeAsFuture { d =>
    val data = Map(
      KeyStringEnum.KeyA -> 0,
      KeyStringEnum.KeyB -> 10,
      KeyStringEnum.KeyC -> 100
    )

    for {
      _ <- c"CREATE (: Data ${data})".query[Unit].execute(d)
      r <- "MATCH (data: Data) RETURN data".query[Map[KeyStringEnum, Int]].single(d)
    } yield {
      assert(r == data)
    }
  }

  it should "fail if retrieving a non-valid value as key string Enum" in executeAsFuture { d =>
    recoverToSucceededIf[IncoercibleException] {
      "RETURN { quax : 7 }".query[Map[KeyStringEnum, Int]].single(d)
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

  final case class DataWithSimpleEnum(name: String, value: SimpleEnum)
  implicit final val DataWithSimpleEnumMapper: neotypes.mappers.ResultMapper[DataWithSimpleEnum] = deriveProductResultMapper

  sealed abstract class ValueEnum (val value: Int, val name: String) extends IntEnumEntry with Product with Serializable
  object ValueEnum extends IntEnum[ValueEnum] with NeotypesIntEnum[ValueEnum] {
    final case object A extends ValueEnum(value = 1, name = "A")
    final case object B extends ValueEnum(value = 3, name = "B")
    final case object C extends ValueEnum(value = 5, name = "C")

    val values = findValues
  }

  final case class DataWithValueEnum(name: String, value: ValueEnum)
  implicit final val DataWithValueEnumMapper: neotypes.mappers.ResultMapper[DataWithValueEnum] = deriveProductResultMapper

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
