package neotypes

import neotypes.internal.syntax.async._

import org.scalatest.FutureOutcome
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.FixtureAsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

/** Base class for testing the Stream.Aux[S, F].guarantee method. */
final class StreamGuaranteeSpec[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseStreamSpec(testkit) with FixtureAsyncFlatSpecLike with Matchers {
  behavior of s"Stream.Aux[${streamName}, ${asyncName}].guarantee"

  import StreamGuaranteeSpec._

  final class StreamGuaranteeFixture {
     var counter = 0

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

    def runStream[T](result: Either[Throwable, T],
                     inputEx: Option[Throwable] = None,
                     finalizerEx: Option[Throwable] = None): Future[List[T]] = {
      def effectFromOption(opt: Option[Throwable]): F[Unit] =
        F.fromEither(opt.toLeft(right = ()))

      fToFuture(streamToFList(
        S.guarantee(
          r = effectFromOption(inputEx)
        ) (
          _ => S.fromF(F.fromEither(result))
        ) { (_, _) =>
          F.delay {
            counter += 1
          } flatMap { _ =>
            effectFromOption(finalizerEx)
          }
        }
      ))
    }
  }

  override final type FixtureParam = StreamGuaranteeFixture

  override final def withFixture(test: OneArgAsyncTest): FutureOutcome =
    super.withFixture(test.toNoArgAsyncTest(new StreamGuaranteeFixture))

  it should "execute finalizer and return the result when nothing fails" in { fixture =>
    val expectedResult = "result"

    fixture.runStream(result = Right(expectedResult)).map { res =>
      res shouldBe List(expectedResult)
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input fails" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStream(inputEx = Some(InputException), result = Right("result"))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return use exception when use fails" in { fixture =>
    recoverToSucceededIf[UseException] {
      fixture.runStream(result = Left(UseException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input and use fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStream(inputEx = Some(InputException), result = Left(UseException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return finalizer exception when finalizer fails" in { fixture =>
    recoverToSucceededIf[FinalizerException]{
      fixture.runStream(result = Right("result"), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input and finalizer fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStream(inputEx = Some(InputException), result = Right("result"), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return use exception when use and finalizer fail" in { fixture =>
    recoverToSucceededIf[UseException] {
      fixture.runStream(result = Left(UseException), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when all fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStream(inputEx = Some(InputException), result = Left(UseException), finalizerEx = Some(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }
}

object StreamGuaranteeSpec {
  final case object InputException extends Exception("Input failed")
  type InputException = InputException.type

  final case object UseException extends Exception("Use failed")
  type UseException = UseException.type

  final case object FinalizerException extends Exception("Finalizer failed")
  type FinalizerException = FinalizerException.type
}
