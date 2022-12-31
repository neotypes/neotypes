package neotypes
package internal.syntax

import model.exceptions.{ChainException, ResultMapperException}

object either {
  implicit class EitherOps[A](private val ea: Either[ResultMapperException, A]) extends AnyVal {
    def and[B](eb: Either[ResultMapperException, B]): Either[ResultMapperException, (A, B)] =
      (ea, eb) match {
        case (Right(a), Right(b)) =>
          Right((a, b))

        case (Right(_), Left(ex)) =>
          Left(ex)

        case (Left(ex), Right(_)) =>
          Left(ex)

        case (Left(exA), Left(exB)) =>
          Left(ChainException.from(exceptions = exA, exB))
      }
  }
}
