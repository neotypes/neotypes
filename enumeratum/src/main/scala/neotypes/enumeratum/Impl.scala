package neotypes
package enumeratum

import exceptions.IncoercibleException
import mappers.{KeyMapper, ParameterMapper, ValueMapper}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

private[enumeratum] object Impl {
  private def getEnumEntryByName[EntryType <: EnumEntry](
    _enum: Enum[EntryType], name: String
  ): Either[IncoercibleException, EntryType] =
    _enum.withNameEither(name).left.map { ex =>
      IncoercibleException(
        message = ex.getMessage,
        cause = Some(ex)
      )
    }

  def parameterMapper[EntryType <: EnumEntry]: ParameterMapper[EntryType] =
    ParameterMapper[String].contramap[EntryType](_.entryName)

  def valueMapper[EntryType <: EnumEntry](_enum: Enum[EntryType]): ValueMapper[EntryType] =
    ValueMapper.instance {
      case (fieldName, value) =>
        ValueMapper[String].to(fieldName, value).flatMap(getEnumEntryByName(_enum, _))
    }

  def keyMapper[EntryType <: EnumEntry](_enum: Enum[EntryType]): KeyMapper[EntryType] =
    KeyMapper[String].imap[EntryType](_.entryName)(getEnumEntryByName(_enum, _))

  object values {
    private def getEnumEntryByValue[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      _enum: ValueEnum[ValueType, EntryType], value: ValueType
    ): Either[IncoercibleException, EntryType] =
      _enum.withValueEither(value).left.map { ex =>
        IncoercibleException(
          message = ex.getMessage,
          cause = Some(ex)
        )
      }

    def parameterMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      implicit mapper: ParameterMapper[ValueType]
    ): ParameterMapper[EntryType] =
      mapper.contramap[EntryType](_.value)

    def valueMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      _enum: ValueEnum[ValueType, EntryType]
    )(
      implicit mapper: ValueMapper[ValueType]
    ): ValueMapper[EntryType] =
      ValueMapper.instance {
        case (fieldName, value) =>
          mapper.to(fieldName, value).flatMap(getEnumEntryByValue(_enum, _))
      }

    def keyMapper[EntryType <: ValueEnumEntry[String]](
      _enum: ValueEnum[String, EntryType]
    ): KeyMapper[EntryType] =
      KeyMapper[String].imap[EntryType](_.value)(getEnumEntryByValue(_enum, _))
  }
}
