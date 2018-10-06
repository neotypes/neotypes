package com.dimafeng.neotype

import java.util
import java.util.function.Consumer

import org.neo4j.driver.v1.{Record, StatementResultCursor, Session => NSession, Transaction => NTransaction}

import scala.collection.JavaConverters._


class Session[F[_] : Effect](session: NSession) {
  def run(): F[_] = {
    ???
  }
}


class Transaction[F[_] : Effect](transaction: NTransaction) {
  def list[T](query: String, params: Map[String, AnyRef] = Map()): F[Seq[T]] = {
    val promise = implicitly[Effect[F]].promise[Seq[T]]
    transaction.runAsync(query, params.asJava).thenAccept(new Consumer[StatementResultCursor] {
      override def accept(t: StatementResultCursor): Unit =
        t.listAsync().thenAccept(new Consumer[util.List[Record]] {
          override def accept(t: util.List[Record]): Unit =
            ??? //promise.complete(t.asScala.map(_.))
        })
    })
    promise.toF
  }
}

trait Effect[F[_]] {
  def promise[T]: Promise[F, T]
}

trait Promise[F[_], T] {
  def complete(v: T): Unit

  def fail[E <: Throwable](v: E): Unit

  def toF: F[T]
}