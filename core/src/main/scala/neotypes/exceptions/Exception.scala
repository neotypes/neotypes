package neotypes.exceptions

import org.neo4j.driver.v1.exceptions.value.Uncoercible

sealed abstract class NeotypesException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)

final case class PropertyNotFoundException(message: String) extends NeotypesException(message)

final case class ConversionException(message: String) extends NeotypesException(message)

final case class NoFieldsDefinedException(message: String) extends NeotypesException(message)

final case class UncoercibleException(message: String, cause: Uncoercible) extends NeotypesException(message, Option(cause))
