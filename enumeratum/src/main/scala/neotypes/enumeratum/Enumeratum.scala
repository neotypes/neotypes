package neotypes
package enumeratum

import mappers.{KeyMapper, ParameterMapper, ResultMapper}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

trait NeotypesEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.parameterMapper[EntryType]

  implicit final val resultMapper: ResultMapper[EntryType] =
    Impl.resultMapper(self)
}

trait NeotypesKeyEnum[EntryType <: EnumEntry] {
  self: Enum[EntryType] =>

  implicit final val keyMapper: KeyMapper[EntryType] =
    Impl.keyMapper(self)
}

sealed trait NeotypesValueEnum[ValueType, EntryType <: ValueEnumEntry[ValueType]] {
  self: ValueEnum[ValueType, EntryType] =>

  implicit def parameterMapper: ParameterMapper[EntryType]
  implicit def resultMapper: ResultMapper[EntryType]
}

trait NeotypesIntEnum[EntryType <: IntEnumEntry] extends NeotypesValueEnum[Int, EntryType] {
  self: ValueEnum[Int, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper(
      mapper = ParameterMapper.IntParameterMapper
    )

  override implicit final val resultMapper: ResultMapper[EntryType] =
    Impl.values.resultMapper(
      _enum = self,
      mapper = ResultMapper.int
    )
}

trait NeotypesLongEnum[EntryType <: LongEnumEntry] extends NeotypesValueEnum[Long, EntryType] {
  self: ValueEnum[Long, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper(
      mapper = ParameterMapper.LongParameterMapper
    )

  override implicit final val resultMapper: ResultMapper[EntryType] =
    Impl.values.resultMapper(
      _enum = self,
      mapper = ResultMapper.long
    )
}

trait NeotypesStringEnum[EntryType <: StringEnumEntry] extends NeotypesValueEnum[String, EntryType] {
  self: ValueEnum[String, EntryType] =>

  override implicit final val parameterMapper: ParameterMapper[EntryType] =
    Impl.values.parameterMapper(
      mapper = ParameterMapper.StringParameterMapper
    )

  override implicit final val resultMapper: ResultMapper[EntryType] =
    Impl.values.resultMapper(
      _enum = self,
      mapper = ResultMapper.string
    )

  implicit final val keyMapper: KeyMapper[EntryType] =
    Impl.values.keyMapper(_enum = self)
}
