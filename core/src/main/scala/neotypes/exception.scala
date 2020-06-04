package neotypes

import org.neo4j.driver.exceptions.value.Uncoercible

object exceptions {
  sealed abstract class NeotypesException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)

  final case class PropertyNotFoundException(message: String) extends NeotypesException(message)

  final case class ConversionException(message: String) extends NeotypesException(message)

  final case class NoFieldsDefinedException(message: String) extends NeotypesException(message)

  final case class IncoercibleException(message: String, cause: Uncoercible) extends NeotypesException(message, Option(cause))
}
