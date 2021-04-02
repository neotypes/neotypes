package neotypes
package enumeratum

import mappers.{KeyMapper, ParameterMapper, ResultMapper, ValueMapper}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

trait NeotypesEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.parameterMapper[EntryType]

  implicit final val valueMapper: ValueMapper[EntryType] =
    Impl.valueMapper(self)

  implicit final val resultMapper: ResultMapper[EntryType] =
    ResultMapper.fromValueMapper[EntryType]
}

trait NeotypesKeyEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val keyMapper: KeyMapper[EntryType] =
    Impl.keyMapper(self)
}

sealed trait NeotypesValueEnum[ValueType, EntryType <: ValueEnumEntry[ValueType]] {
  self: ValueEnum[ValueType, EntryType] =>

  implicit def parameterMapper: ParameterMapper[EntryType]
  implicit def valueMapper: ValueMapper[EntryType]
  implicit def resultMapper: ResultMapper[EntryType]
}

trait NeotypesIntEnum[EntryType <: IntEnumEntry] extends NeotypesValueEnum[Int, EntryType] {
  self: ValueEnum[Int, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper[Int, EntryType]

  override implicit final val valueMapper: ValueMapper[EntryType] =
    Impl.values.valueMapper(self)

  override implicit final val resultMapper: ResultMapper[EntryType] =
    ResultMapper.fromValueMapper[EntryType]
}

trait NeotypesLongEnum[EntryType <: LongEnumEntry] extends NeotypesValueEnum[Long, EntryType] {
  self: ValueEnum[Long, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper[Long, EntryType]

  override implicit final val valueMapper: ValueMapper[EntryType] =
    Impl.values.valueMapper(self)

  override implicit final val resultMapper: ResultMapper[EntryType] =
    ResultMapper.fromValueMapper[EntryType]
}

trait NeotypesStringEnum[EntryType <: StringEnumEntry] extends NeotypesValueEnum[String, EntryType] {
  self: ValueEnum[String, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper[String, EntryType]

  override implicit final val valueMapper: ValueMapper[EntryType] =
    Impl.values.valueMapper(self)

  override implicit final val resultMapper: ResultMapper[EntryType] =
    ResultMapper.fromValueMapper[EntryType]

  implicit final val keyMapper: KeyMapper[EntryType] =
    Impl.values.keyMapper(self)
}
