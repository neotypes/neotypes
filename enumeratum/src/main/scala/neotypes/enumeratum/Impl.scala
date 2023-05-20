package neotypes
package enumeratum

import mappers.{KeyMapper, ParameterMapper, ResultMapper}
import model.exceptions.{IncoercibleException, KeyMapperException}

import _root_.enumeratum.{Enum, EnumEntry}
import _root_.enumeratum.values._

private[enumeratum] object Impl {
  private def getEnumEntryByName[EntryType <: EnumEntry](
    _enum: Enum[EntryType], name: String
  ): Either[KeyMapperException, EntryType] =
    _enum.withNameEither(name).left.map { ex =>
      KeyMapperException(key = name, cause = ex)
    }

  def parameterMapper[EntryType <: EnumEntry]: ParameterMapper[EntryType] =
    ParameterMapper.StringParameterMapper.contramap(_.entryName)

  def resultMapper[EntryType <: EnumEntry](_enum: Enum[EntryType]): ResultMapper[EntryType] =
    ResultMapper.string.emap { name =>
      getEnumEntryByName(_enum, name)
    }

  def keyMapper[EntryType <: EnumEntry](_enum: Enum[EntryType]): KeyMapper[EntryType] =
    KeyMapper.string.imap[EntryType](_.entryName)(getEnumEntryByName(_enum, _))

  object values {
    private def getEnumEntryByValue[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      _enum: ValueEnum[ValueType, EntryType], value: ValueType
    ): Either[IncoercibleException, EntryType] =
      _enum.withValueEither(value).left.map { ex =>
        IncoercibleException(
          message = s"Couldn't decode ${value} as ${_enum}",
          cause = Some(ex)
        )
      }

    def parameterMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      mapper: ParameterMapper[ValueType]
    ): ParameterMapper[EntryType] =
      mapper.contramap(_.value)

    def resultMapper[ValueType, EntryType <: ValueEnumEntry[ValueType]](
      _enum: ValueEnum[ValueType, EntryType],
      mapper: ResultMapper[ValueType]
    ): ResultMapper[EntryType] =
      mapper.emap { value =>
        getEnumEntryByValue(_enum, value)
      }

    def keyMapper[EntryType <: ValueEnumEntry[String]](
      _enum: ValueEnum[String, EntryType]
    ): KeyMapper[EntryType] =
      KeyMapper.string.imap[EntryType](_.value) { name =>
        getEnumEntryByValue(_enum, name).left.map { ex =>
          KeyMapperException(key = name, cause = ex)
        }
      }
  }
}
