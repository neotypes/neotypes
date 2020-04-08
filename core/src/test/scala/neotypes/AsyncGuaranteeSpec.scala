package neotypes

import neotypes.internal.syntax.async._
import org.scalatest.EitherValues
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.reflect.ClassTag

/** Base class for testing the Async[F].guarantee method. */
abstract class AsyncGuaranteeSpec[F[_]] (implicit ct: ClassTag[F[_]]) extends AnyFlatSpec with Matchers with EitherValues {
  private val effectName: String = ct.runtimeClass.getCanonicalName
  behavior of s"Async[${effectName}].guarantee"

  def fToEither[T](f: F[T]): Either[Throwable, T]
  implicit def F: Async[F]

  trait AsyncGuaranteeFixture {
    private[this] var counter = 0

    final def assertFinalizerWasCalledOnlyOnce: Assertion =
      withClue("Finalizer was called more than once -") {
        this.counter shouldBe 1
      }

    final def assertFinalizerWasNotCalled: Assertion =
      withClue("Finalizer was called -") {
        this.counter shouldBe 0
      }

    final def run[T](result: Either[Throwable, T],
                     inputEx: Option[Throwable] = None,
                     finalizerEx: Option[Throwable] = None): Either[Throwable, T] = {
      def fromOption(opt: Option[Throwable]): F[Unit] =
        opt.fold(ifEmpty = F.delay(()))(ex => F.failed(ex))

      fToEither(
        F.guarantee[Unit, T](
          fa = fromOption(inputEx)
        )(
          _ => result.fold(ex => F.failed(ex), t => F.delay(t))
        ) { (_, _) =>
          F.delay {
            counter += 1
          } flatMap { _ =>
            fromOption(finalizerEx)
          }
        }
      )
    }
  }

  it should "execute finalizer and return the result when nothing fails" in new AsyncGuaranteeFixture {
    val expectedResult = "result"

    val res = run(result = Right(expectedResult))

    res.right.value shouldBe expectedResult
    assertFinalizerWasCalledOnlyOnce
  }

  it should "not execute finalizer and return input exception when input fails" in new AsyncGuaranteeFixture {
    val inputEx = new Exception("Input failed")

    val ex = run(inputEx = Some(inputEx), result = Right("result"))

    ex.left.value shouldBe inputEx
    assertFinalizerWasNotCalled
  }

  it should "execute finalizer and return use exception when use fails" in new AsyncGuaranteeFixture {
    val useEx = new Exception("Use failed")

    val ex = run(result = Left(useEx))

    ex.left.value shouldBe useEx
    assertFinalizerWasCalledOnlyOnce
  }

  it should "not execute finalizer and return input exception when input and use fail" in new AsyncGuaranteeFixture {
    val inputEx = new Exception("Input failed")
    val useEx = new Exception("Use failed")

    val ex = run(inputEx = Some(inputEx), result = Left(useEx))

    ex.left.value shouldBe inputEx
    assertFinalizerWasNotCalled
  }

  it should "execute finalizer and return finalizer exception when finalizer fails" in new AsyncGuaranteeFixture {
    val finalizerEx = new Exception("Finalizer failed!")

    val ex = run(result = Right("result"), finalizerEx = Some(finalizerEx))

    ex.left.value shouldBe finalizerEx
    assertFinalizerWasCalledOnlyOnce
  }

  it should "not execute finalizer and return input exception when input and finalizer fail" in new AsyncGuaranteeFixture {
    val inputEx = new Exception("Input failed")
    val finalizerEx = new Exception("Finalizer failed!")

    val ex = run(inputEx = Some(inputEx), result = Right("result"), finalizerEx = Some(finalizerEx))

    ex.left.value shouldBe inputEx
    assertFinalizerWasNotCalled
  }

  it should "execute finalizer and return use exception when use and finalizer fail" in new AsyncGuaranteeFixture {
    val useEx = new Exception("Input failed")
    val finalizerEx = new Exception("Finalizer failed!")

    val ex = run(result = Left(useEx), finalizerEx = Some(finalizerEx))

    ex.left.value shouldBe useEx
    assertFinalizerWasCalledOnlyOnce
  }

  it should "not execute finalizer and return input exception when all fail" in new AsyncGuaranteeFixture {
    val inputEx = new Exception("Input failed")
    val useEx = new Exception("Use failed")
    val finalizerEx = new Exception("Finalizer failed!")

    val ex = run(inputEx = Some(inputEx), result = Left(useEx), finalizerEx = Some(finalizerEx))

    ex.left.value shouldBe inputEx
    assertFinalizerWasNotCalled
  }
}
