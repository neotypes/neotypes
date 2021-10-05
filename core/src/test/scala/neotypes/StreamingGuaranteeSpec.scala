package neotypes

import neotypes.internal.syntax.async._
import org.scalatest.FutureOutcome
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.FixtureAsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

/** Base class for testing the Stream[S, F].guarantee method. */
final class StreamingGuaranteeSpec[S[_], F[_]](testkit: StreamTestkit[S, F]) extends BaseStreamSpec(testkit) with FixtureAsyncFlatSpecLike with Matchers {
  behavior of s"Stream[${effectName}].guarantee"

  import StreamingGuaranteeSpec._

  final class StreamingGuaranteeFixture {
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

    def runStreaming[T](result: Either[Throwable, T],
               inputEx: Either[Throwable, Unit] = Right(()),
               finalizerEx: Either[Throwable, Unit] = Right(())): Future[List[T]] = {

      def streamFromEither(either: Either[Throwable, T]): S[T] =
        S.fromF(F.fromEither(either))

      val res =
        S.guarantee(r = F.fromEither(inputEx))(_ => streamFromEither(result))
        { (_, _) =>
           F.delay {
             counter += 1
           } flatMap { _ =>
             F.fromEither(finalizerEx)
           }
        }

      fToFuture(streamToFList(res))
    }
  }

  override final type FixtureParam = StreamingGuaranteeFixture

  override final def withFixture(test: OneArgAsyncTest): FutureOutcome =
    super.withFixture(test.toNoArgAsyncTest(new StreamingGuaranteeFixture))

  it should "execute finalizer and return the result when nothing fails" in { fixture =>
    val expectedResult = "result"

    fixture.runStreaming(result = Right(expectedResult)).map { res =>
      res shouldBe List(expectedResult)
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input fails" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStreaming(inputEx = Left(InputException), result = Right("result"))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

//  it should "execute finalizer and return use exception when use fails" in { fixture =>
//    recoverToSucceededIf[UseException] {
//      fixture.runStreaming(result = Left(UseException))
//    } map { _ =>
//      fixture.assertFinalizerWasCalledOnlyOnce
//    }
//  }

  it should "not execute finalizer and return input exception when input and use fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStreaming(inputEx = Left(InputException), result = Left(UseException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }

  it should "execute finalizer and return finalizer exception when finalizer fails" in { fixture =>
    recoverToSucceededIf[FinalizerException]{
      fixture.runStreaming(result = Right("result"), finalizerEx = Left(FinalizerException))
    }.map { r =>
      fixture.assertFinalizerWasCalledOnlyOnce
    }
  }

  it should "not execute finalizer and return input exception when input and finalizer fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStreaming(inputEx = Left(InputException), result = Right("result"), finalizerEx = Left(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }
//
//  it should "execute finalizer and return use exception when use and finalizer fail" in { fixture =>
//    recoverToSucceededIf[UseException] {
//      fixture.runStreaming(result = Left(UseException), finalizerEx = Left(FinalizerException))
//    } map { _ =>
//      fixture.assertFinalizerWasCalledOnlyOnce
//    }
//  }

  it should "not execute finalizer and return input exception when all fail" in { fixture =>
    recoverToSucceededIf[InputException] {
      fixture.runStreaming(inputEx = Left(InputException), result = Left(UseException), finalizerEx = Left(FinalizerException))
    } map { _ =>
      fixture.assertFinalizerWasNotCalled
    }
  }
}

object StreamingGuaranteeSpec {
  final case object InputException extends Exception("Input failed")
  type InputException = InputException.type

  final case object UseException extends Exception("Use failed")
  type UseException = UseException.type

  final case object FinalizerException extends Exception("Finalizer failed")
  type FinalizerException = FinalizerException.type
}



