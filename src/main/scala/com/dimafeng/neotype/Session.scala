package com.dimafeng.neotype

import java.util
import java.util.List
import java.util.function.Function

import org.neo4j.driver.v1.{StatementResultCursor, Value}
import org.neo4j.driver.v1.{Record, StatementResultCursor, Session => NSession, Transaction => NTransaction}

import scala.collection.JavaConverters._


class Session[F[_] : Async](session: NSession) {
  def beginTransaction(): Transaction[F] = {
    new Transaction(session.beginTransaction())
  }
}


class Transaction[F[_]](transaction: NTransaction)(implicit val F: Async[F]) {

  def list[T: RecordMarshallable](query: String, params: Map[String, AnyRef] = Map()): F[Seq[T]] = {
    val marshaller = implicitly[RecordMarshallable[T]]

    F.async[Seq[T]] { cb =>
      transaction
        .runAsync(query, params.asJava)
        .thenCompose(_.listAsync())
        .thenAccept((result: util.List[Record]) =>
          cb {
            val list = result.asScala
              .map(_.fields().asScala.map(p => p.key() -> p.value()))
              .map(marshaller.to)

            list.find(_.isLeft).map(_.map(_=>Seq[T]())).getOrElse(Right(list.collect { case Right(v) => v }))
          }
        ).exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def close() = //: F[_] = {
    transaction.close()

  //null // TODO
  //}
}

