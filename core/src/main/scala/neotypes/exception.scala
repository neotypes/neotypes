package neotypes

object exceptions {
  sealed abstract class NeotypesException(message: String, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)

  final case class PropertyNotFoundException(message: String) extends NeotypesException(message)

  final case class IncoercibleException(message: String, cause: Option[Throwable] = None) extends NeotypesException(message, cause)

  final case object TransactionWasNotCreatedException extends NeotypesException(message = "Couldn't create a transaction")

  final case object CancellationException extends NeotypesException(message = "An operation was cancelled")
}
