package neotypes

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.concurrent.ScalaFutures
class FutureAsyncSpec extends FlatSpec with Matchers {
  implicit val ec = ExecutionContext.Implicits.global

  trait FutureAsyncFixture {
    var counter = 0
    var resourceIsOpen = true
    val fa = Future.successful(1)
    val f: Int => Future[String] = i => Future.successful(i.toString)
    val finalizer = { (_: Int, _: Option[Throwable]) => {Future.successful {resourceIsOpen = false}}}
  }

  "async future" should "execute finalizer" in new FutureAsyncFixture {
    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer)){
      res =>
        res shouldBe "1"
        resourceIsOpen shouldBe false //resource is closed
    }
  }

  "async future" should "not execute finalizer when fa fails and return the exception" in new FutureAsyncFixture {
    override val fa = Future.failed(new Exception("Boom!"))

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        ex.getMessage shouldBe "Boom!"
        resourceIsOpen shouldBe true //resource is left open
    }
  }

  "async future" should "execute finalizer when f fails and return the exception" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    override val f: Int => Future[String] = _ => Future.failed(runtimeEx)

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        ex shouldBe runtimeEx
        resourceIsOpen shouldBe false //resource was closed
    }
  }

  "async future" should "execute finalizer and return f exception when f and finalizer fail" in new FutureAsyncFixture {
    val runtimeEx = new Exception("Boom!")
    override val f: Int => Future[String] = _ => Future.failed(runtimeEx)
    override val finalizer = { (_: Int, _: Option[Throwable]) => {Future.failed(new Exception("Finalizer Failed!"))} }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        ex.getMessage shouldBe "Boom!"
        resourceIsOpen shouldBe true //resource was not closed
    }
  }

  "async future" should "return finalizer exception" in new FutureAsyncFixture {
    override val finalizer = { (_: Int, _: Option[Throwable]) => {
      counter += 1
      Future.failed(new Exception(s"Finalizer failure count: ${counter}!"))
      }
    }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        counter shouldBe 2
        ex.getMessage shouldBe "Finalizer failure count: 1!" //first failure
        resourceIsOpen shouldBe true //resource was not closed
    }
  }

  "async future" should "return finalizer exception when finalizer fails and then succeeds" in new FutureAsyncFixture {
    override val finalizer = { (_: Int, _: Option[Throwable]) => {
      counter = counter + 1
      counter match{
        case 1 => Future.failed(new Exception(s"Finalizer failure count: ${counter}!"))
        case _ => Future.successful{ resourceIsOpen = false }
      }
    }
    }

    ScalaFutures.whenReady(Async.futureAsync.guarantee(fa)(f)(finalizer).failed){
      ex =>
        counter shouldBe 2
        ex.getMessage shouldBe "Finalizer failure count: 1!" //first finalizer exception
        resourceIsOpen shouldBe false //resource was closed successfully
    }
  }

}
