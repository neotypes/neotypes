package neotypes

import neotypes.internal.syntax.async._
import org.scalatest.Assertion

import scala.concurrent.Future

/** Base class for testing the Async[F].guarantee method. */
final class AsyncGuaranteeSpec[F[_]](testkit: AsyncTestkit[F]) extends BaseAsyncSpec(testkit) {
  behavior of s"Async[${asyncName}].guarantee"

  import AsyncGuaranteeSpec._

  final class AsyncGuaranteeFixture {
    private[this] var counter = 0

    def assertFinalizerWasCalledOnlyOnce: Assertion = {
      withClue("Finalizer was not called -") {
        this.counter should not be 0
      }

      withClue("Finalizer was called more than once -") {
        this.counter shouldBe 1
      }
    }

    def assertFinalizerWasNotCalled: Assertion =
      withClue("Finalizer was called -") {
        this.counter shouldBe 0
      }

    def run[T](
      result: Either[Throwable, T],
      inputEx: Option[Throwable] = None,
      finalizerEx: Option[Throwable] = None
    ): Future[T] = {

      fToFuture(
        F.guarantee[Unit, T](
          fa = F.fromEither(inputEx.toLeft(right = ()))
        )(
          f = _ => F.fromEither(result)
        ) { (_, _) =>
          F.delay {
            counter += 1
          } flatMap { _ =>
            F.fromEither(finalizerEx.toLeft(right = ()))
          }
        }
      )
    }
  }

  it should "execute finalizer and return the result when nothing fails" in {
    val fixture = new AsyncGuaranteeFixture

    val expectedResult = "result"

    fixture.run(result = Right(expectedResult)).map { res =>
      res shouldBe expectedResult
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input fails" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[InputException] {
      fixture.run(inputEx = Some(InputException), result = Right("result"))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return use exception when use fails" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[UseException] {
      fixture.run(result = Left(UseException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input and use fail" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[InputException] {
      fixture.run(inputEx = Some(InputException), result = Left(UseException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return finalizer exception when finalizer fails" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[FinalizerException] {
      fixture.run(result = Right("result"), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input and finalizer fail" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[InputException] {
      fixture.run(inputEx = Some(InputException), result = Right("result"), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return use exception when use and finalizer fail" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[UseException] {
      fixture.run(result = Left(UseException), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when all fail" in {
    val fixture = new AsyncGuaranteeFixture

    recoverToSucceededIf[InputException] {
      fixture.run(inputEx = Some(InputException), result = Left(UseException), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }
}

object AsyncGuaranteeSpec {
  case object InputException extends Exception("Input failed")
  type InputException = InputException.type

  case object UseException extends Exception("Use failed")
  type UseException = UseException.type

  case object FinalizerException extends Exception("Finalizer failed")
  type FinalizerException = FinalizerException.type
}
