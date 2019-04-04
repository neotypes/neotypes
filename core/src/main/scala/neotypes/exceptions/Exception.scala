package neotypes.exceptions

import org.neo4j.driver.v1.exceptions.value.Uncoercible

sealed class NeotypesException(message: String, cause: Throwable = null) extends Exception(message, cause)

case class PropertyNotFoundException(message: String) extends NeotypesException(message)

case class ConversionException(message: String) extends NeotypesException(message)

case class NoFieldsDefinedException(message: String) extends NeotypesException(message)

case class UncoercibleException(message: String, cause: Uncoercible) extends NeotypesException(message, cause)
