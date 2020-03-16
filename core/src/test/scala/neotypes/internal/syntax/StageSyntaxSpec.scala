package neotypes
package internal.syntax

import java.util.concurrent.{CompletableFuture, CompletionStage}
import neotypes.internal.syntax.stage._
import org.scalatest.{AsyncWordSpec, Matchers}
import scala.concurrent.Future
import scala.reflect.ClassTag

/** Base class for testing the CompletionStage syntax. */
abstract class StageSyntaxSpec[F[_]] (implicit ct: ClassTag[F[_]]) extends AsyncWordSpec with Matchers {
  private val effectName: String = ct.runtimeClass.getCanonicalName

  def fToFuture[T](f: F[T]): Future[T]
  def F: Async[F]

  import StageSyntaxSpec.CustomException

  private def testAccept[T](result: => Either[Throwable, T],
                            inputEx: Option[Throwable] = None): Future[T] =
    fToFuture(
      F.async[T] { cb =>
        val completionStage =
          inputEx.fold(ifEmpty = CompletableFuture.completedFuture(())) { ex =>
            val completedFuture = new CompletableFuture[Unit]()
            completedFuture.completeExceptionally(ex)

            completedFuture
          }

        completionStage.accept(cb)(_ => result)
      }
    )

  private def testAcceptExceptionally[T](result: => Either[Throwable, T], inputEx: Option[Throwable] = None)
                                        (recover: PartialFunction[Throwable, Either[Throwable, T]]): Future[T] =
    fToFuture(
      F.async[T] { cb =>
        val completionStage =
          inputEx.fold(ifEmpty = CompletableFuture.completedFuture(())) { ex =>
            val completedFuture = new CompletableFuture[Unit]()
            completedFuture.completeExceptionally(ex)

            completedFuture
          }

        completionStage.acceptExceptionally(cb)(_ => result)(recover)
      }
    )

  private def testAcceptVoid(inputEx: Option[Throwable] = None): Future[Unit] =
    fToFuture(
      F.async[Unit] { cb =>
        val completionStage =
          inputEx.fold(ifEmpty = CompletableFuture.completedFuture[Void](None.orNull)) { ex =>
            val completedFuture = new CompletableFuture[Void]()
            completedFuture.completeExceptionally(ex)

            completedFuture
          }

        completionStage.acceptVoid(cb)
      }
    )

  private def provide = afterWord("provide")

  s"The CompletionStage syntax used with ${effectName}" should provide {
    "an accept operator" which {
      "returns the passed value in the result" in {
        val expectedResult = "result"

        testAccept(result = Right(expectedResult)).map { result =>
          result shouldBe expectedResult
        }
      }

      "returns the passed exception in the result" in {
        val expectedEx = CustomException("use failed")

        recoverToExceptionIf[CustomException] {
          testAccept(result = Left(expectedEx))
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the throwed exception in the result" in {
        val expectedEx = CustomException("use failed")

        recoverToExceptionIf[CustomException] {
          testAccept(result = throw expectedEx)
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the original exception if the base completation stage failed" in {
        val expectedEx = CustomException("input failed")

        recoverToExceptionIf[CustomException] {
          testAccept(inputEx = Some(expectedEx), result = Right("result"))
        } map { ex =>
          ex shouldBe expectedEx
        }
      }
    }

    "an acceptExceptionally operator" which {
      "returns the passed value in the result" in {
        val expectedResult = "result"

        testAcceptExceptionally(result = Right(expectedResult)) {
          case ex => Left(ex)
        } map { result =>
          result shouldBe expectedResult
        }
      }

      "returns the passed exception in the result" in {
        val expectedEx = CustomException("use failed")

        recoverToExceptionIf[CustomException] {
          testAcceptExceptionally(result = Left(expectedEx)) {
            case ex => Left(ex)
          }
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the throwed exception in the result if not recovered" in {
        val expectedEx = CustomException("use failed")

        recoverToExceptionIf[CustomException] {
          testAcceptExceptionally(result = throw expectedEx) {
            case ex => Left(ex)
          }
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the original exception if the base completation stage failed and not recovered" in {
        val expectedEx = CustomException("input failed")

        recoverToExceptionIf[CustomException] {
          testAcceptExceptionally(inputEx = Some(expectedEx), result = Right("result")) {
            case ex => Left(ex)
          }
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the recovered value if the result throws an exception" in {
        val expectedValue = "result"

        testAcceptExceptionally(result = throw CustomException("use faield")) {
          case CustomException(_) => Right(expectedValue)
        } map { result =>
          result shouldBe expectedValue
        }
      }

      "returns the recovered exception if the result throws an exception" in {
        val expectedEx = CustomException("expected exception")

        recoverToExceptionIf[CustomException] {
          testAcceptExceptionally(result = throw CustomException("use faield")) {
            case CustomException(_) => Left(expectedEx)
          }
        } map { ex =>
          ex shouldBe expectedEx
        }
      }

      "returns the recovered value if the base completation stage failed" in {
        val expectedValue = "result"

        testAcceptExceptionally(inputEx = Some(CustomException("input failed")), result = Right("result")) {
          case CustomException(_) => Right(expectedValue)
        } map { result =>
          result shouldBe expectedValue
        }
      }

      "returns the recovered exception if the base completation stage failed" in {
        val expectedEx = CustomException("expected exception")

        recoverToExceptionIf[CustomException] {
          testAcceptExceptionally(inputEx = Some(CustomException("input failed")), result = Right("result")) {
            case CustomException(_) => Left(expectedEx)
          }
        } map { ex =>
          ex shouldBe expectedEx
        }
      }
    }

    "an acceptVoid method" which {
      "returns unit" in {
        val expectedResult = ()
        testAcceptVoid().map { result =>
          result shouldBe expectedResult
        }
      }

      "returns the original exception if the base completation stage failed" in {
        val expectedEx = CustomException("input failed")

        recoverToExceptionIf[CustomException] {
          testAcceptVoid(inputEx = Some(expectedEx))
        } map { ex =>
          ex shouldBe expectedEx
        }
      }
    }
  }
}

object StageSyntaxSpec {
  final case class CustomException(msg: String) extends Throwable(msg)
}
