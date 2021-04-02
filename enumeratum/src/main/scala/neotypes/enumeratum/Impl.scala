package neotypes
package enumeratum

import exceptions.IncoercibleException
import mappers.{KeyMapper, ParameterMapper, ValueMapper}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

private[enumeratum] object Impl {
  private def getEnumEntryByName[EntryType <: EnumEntry](
    enum: Enum[EntryType], name: String
  ): Either[IncoercibleException, EntryType] =
    enum.withNameEither(name).left.map { ex =>
      IncoercibleException(ex.getMessage, None.orNull)
    }

  def parameterMapper[EntryType <: EnumEntry]: ParameterMapper[EntryType] =
    ParameterMapper[String].contramap[EntryType](_.entryName)

  def valueMapper[EntryType <: EnumEntry](enum: Enum[EntryType]): ValueMapper[EntryType] =
    ValueMapper.instance {
      case (fieldName, value) =>
        ValueMapper[String].to(fieldName, value).flatMap(getEnumEntryByName(enum, _))
    }

  def keyMapper[EntryType <: EnumEntry](enum: Enum[EntryType]): KeyMapper[EntryType] =
    KeyMapper[String].imap[EntryType](_.entryName)(getEnumEntryByName(enum, _))

  object values {
    private def getEnumEntryByValue[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      enum: ValueEnum[ValueType, EntryType], value: ValueType
    ): Either[IncoercibleException, EntryType] =
      enum.withValueEither(value).left.map { ex =>
        IncoercibleException(ex.getMessage, None.orNull)
      }

    def parameterMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      implicit mapper: ParameterMapper[ValueType]
    ): ParameterMapper[EntryType] =
      mapper.contramap[EntryType](_.value)

    def valueMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      enum: ValueEnum[ValueType, EntryType]
    )(
      implicit mapper: ValueMapper[ValueType]
    ): ValueMapper[EntryType] =
      ValueMapper.instance {
        case (fieldName, value) =>
          mapper.to(fieldName, value).flatMap(getEnumEntryByValue(enum, _))
      }

    def keyMapper[EntryType <: ValueEnumEntry[String]](
      enum: ValueEnum[String, EntryType]
    ): KeyMapper[EntryType] =
      KeyMapper[String].imap[EntryType](_.value)(getEnumEntryByValue(enum, _))
  }
}
