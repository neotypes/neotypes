package com.dimafeng.neotype

import java.util

import scala.concurrent.Future
//import java.util.concurrent.Future
import java.util.function.Consumer

import org.neo4j.driver.v1.{Record, StatementResultCursor, Session => NSession, Transaction => NTransaction}

import scala.collection.JavaConverters._
import scala.concurrent.{Promise => SPromise}
import scala.util.Success


class Session[F[_] : Effect](session: NSession) {
  def beginTransaction(): Transaction[F] = {
    new Transaction(session.beginTransaction())
  }
}


class Transaction[F[_] : Effect](transaction: NTransaction) {

  def list[T: RecordMarshallable](query: String, params: Map[String, AnyRef] = Map()): F[Seq[T]] = {
    val promise = implicitly[Effect[F]].promise[Seq[T]]
    val marshaller = implicitly[RecordMarshallable[T]]
    transaction.runAsync(query, params.asJava)
      .thenAccept(
        (t: StatementResultCursor) =>
          t.listAsync().thenAccept((t: util.List[Record]) =>
            promise.complete(t.asScala.map(marshaller.to))
          )
      )
    promise.toF
  }

  def close() = //: F[_] = {
    transaction.close()
    //null // TODO
  //}
}

trait Effect[F[_]] {
  def promise[T]: Promise[F, T]
}

trait Promise[F[_], T] {
  def complete(v: T): Unit

  def fail[E <: Throwable](v: E): Unit

  def toF: F[T]
}

object Effect {

  implicit object FutureEffect extends Effect[Future] {
    override def promise[T]: Promise[Future, T] = {
      val p = SPromise[T]
      new Promise[Future, T] {
        override def complete(v: T): Unit = p.complete(Success(v))

        override def fail[E <: Throwable](v: E): Unit = p.failure(v)

        override def toF: Future[T] = p.future
      }
    }
  }

}