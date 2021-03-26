package neotypes
package enumeratum

import mappers.{KeyMapper, ParameterMapper, ResultMapper, ValueMapper}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

trait NeotypesEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  implicit final val resultMapper: ResultMapper[EntryType] = ???
  implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesKeyEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val keyMapper: KeyMapper[EntryType] = ???
}

sealed trait NeotypesValueEnum[ValueType, EntryType <: ValueEnumEntry[ValueType]] {
  self: ValueEnum[ValueType, EntryType] =>

  implicit def parameterMapper: ParameterMapper[EntryType]
  implicit def resultMapper: ResultMapper[EntryType]
  implicit def valueMapper: ValueMapper[EntryType]
}

trait NeotypesByteEnum[EntryType <: ByteEnumEntry] extends NeotypesValueEnum[Byte, EntryType] {
  self: ValueEnum[Byte, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesCharEnum[EntryType <: CharEnumEntry] extends NeotypesValueEnum[Char, EntryType] {
  self: ValueEnum[Char, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesIntEnum[EntryType <: IntEnumEntry] extends NeotypesValueEnum[Int, EntryType] {
  self: ValueEnum[Int, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesLongEnum[EntryType <: LongEnumEntry] extends NeotypesValueEnum[Long, EntryType] {
  self: ValueEnum[Long, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesShortEnum[EntryType <: ShortEnumEntry] extends NeotypesValueEnum[Short, EntryType] {
  self: ValueEnum[Short, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???
}

trait NeotypesStringEnum[EntryType <: StringEnumEntry] extends NeotypesValueEnum[String, EntryType] {
  self: ValueEnum[String, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] = ???
  override implicit final val resultMapper: ResultMapper[EntryType] = ???
  override implicit final val valueMapper: ValueMapper[EntryType] = ???

  implicit final val keyMapper: KeyMapper[EntryType] = ???
}
