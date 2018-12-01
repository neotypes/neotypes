package neotypes.excpetions

import org.neo4j.driver.v1.exceptions.value.Uncoercible

case class PropertyNotFoundException(message: String) extends Exception(message)

case class ConversionException(message: String) extends Exception(message)

case class NoFieldsDefinedException(message: String) extends Exception(message)

case class UncoercibleException(message: String, cause: Uncoercible) extends Exception(message, cause)
