package neotypes

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.concurrent.ScalaFutures
class FutureAsyncGuaranteeSpec extends FlatSpec with Matchers {
  implicit val ec = ExecutionContext.Implicits.global

  trait FutureAsyncFixture {
    var counter = 0
    var resourceIsOpen = true
    val fa = Future.successful(1)
    val f = (i: Int) => Future.successful(i.toString)
    val finalizer = (_: Int, _: Option[Throwable]) => Future {
      counter += 1
      resourceIsOpen = false
    }
  }

  it should "execute finalizer" in new FutureAsyncFixture {
    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer)) {
      res =>
        res shouldBe "1"
        resourceIsOpen shouldBe false // Resource is closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }

  it should "execute finalizer when fa fails and return the exception" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    override val fa = Future.failed(runtimeEx)

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed) {
      ex =>
        ex shouldBe runtimeEx
        resourceIsOpen shouldBe false // Resource is closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }

  it should "execute finalizer when f fails and return the exception" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    override val f = _ => Future.failed(runtimeEx)

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed) {
      ex =>
        ex shouldBe runtimeEx
        resourceIsOpen shouldBe false // Resource is closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }

  it should "return finalizer exception" in new FutureAsyncFixture {
    val finalizerEx = new Exception("Finalizer Failed!")
    override val finalizer = (_: Int, _: Option[Throwable]) => {
      counter += 1
      Future.failed(finalizerEx)
    }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        ex shouldBe finalizerEx
        resourceIsOpen shouldBe true // Resource was not closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }

  it should "execute finalizer and return fa exception when fa and finalizer fail" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    val finalizerEx = new Exception("Finalizer Failed!")
    override val fa = Future.failed(runtimeEx)
    override val finalizer = (_: Int, _: Option[Throwable]) => {
      counter += 1
      Future.failed(finalizerEx)
    }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed) {
      ex =>
        ex shouldBe runtimeEx
        resourceIsOpen shouldBe true // Resource was not closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }

  it should "execute finalizer and return f exception when f and finalizer fail" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    val finalizerEx = new Exception("Finalizer Failed!")
    override val f = _ => Future.failed(runtimeEx)
    override val finalizer = (_: Int, _: Option[Throwable]) => {
      counter += 1
      Future.failed(finalizerEx)
    }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed) {
      ex =>
        ex shouldBe runtimeEx
        resourceIsOpen shouldBe true // Resource was not closed.
        counter shouldBe 1 // Finalizer was called only once.
    }
  }
}
