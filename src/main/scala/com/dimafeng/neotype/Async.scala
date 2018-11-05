package com.dimafeng.neotype

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait Async[F[_]] {
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]
}

object Async {

  implicit object FutureAsync extends Async[Future] {

    override def async[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
      val p = scala.concurrent.Promise[A]
      cb {
        case Right(res) => p.complete(Success(res))
        case Left(ex) => p.complete(Failure(ex))
      }
      p.future
    }
  }

}
