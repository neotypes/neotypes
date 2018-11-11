package neotypes

import java.util

import org.neo4j.driver.v1.{Record, Transaction => NTransaction}

import scala.collection.JavaConverters._

class Transaction[F[+ _]](transaction: NTransaction)(implicit F: Async[F]) {

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

            list.find(_.isLeft).map(_.asInstanceOf[Either[Exception, Seq[T]]]).getOrElse(Right(list.collect { case Right(v) => v }))
          }
        ).exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def single[T: RecordMarshallable](query: String, params: Map[String, AnyRef] = Map()): F[T] = {
    val marshaller = implicitly[RecordMarshallable[T]]

    F.async[T] { cb =>
      transaction.runAsync(query, params.asJava)
        .thenCompose(_.singleAsync())
        .thenAccept((res: Record) => cb {
          marshaller.to(res.fields().asScala.map(p => p.key() -> p.value()))
        })
        .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
    }
  }

  def commit(): F[Unit] = F.async(cb =>
    transaction.commitAsync()
      .thenAccept(_ => cb(Right(())))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
  )

  def rollback(): F[Unit] = F.async(cb =>
    transaction.rollbackAsync()
      .thenAccept(_ => cb(Right(())))
      .exceptionally(ex => cb(Left(ex)).asInstanceOf[Void])
  )
}
