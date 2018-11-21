package neotypes.excpetions

case class PropertyNotFoundException(message: String) extends Exception(message)
case class ConversionException(message: String) extends Exception(message)
case class NoFieldsDefinedException(message: String) extends Exception(message)
