package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.v1.{AccessMode, Driver => NDriver, Session => NSession}

import scala.collection.JavaConverters._
import scala.language.higherKinds

final class Driver[F[_]](driver: NDriver)(implicit F: Async[F]) {
  def session(): F[Session[F]] =
    session(accessMode = AccessMode.READ)

  def session(accessMode: AccessMode, bookmarks: String*): F[Session[F]] =
    F.delay(
      new Session(
        bookmarks match {
          case Seq()         => driver.session(accessMode)
          case Seq(bookmark) => driver.session(accessMode, bookmark)
          case _             => driver.session(accessMode, bookmarks.asJava)
        }
      )
    )

  def readSession[T](sessionWork: Session[F] => F[T]): F[T] =
    withSession(AccessMode.READ)(sessionWork)

  def writeSession[T](sessionWork: Session[F] => F[T]): F[T] =
    withSession(AccessMode.WRITE)(sessionWork)

  private[this] def withSession[T](accessMode: AccessMode)(sessionWork: Session[F] => F[T]): F[T] =
    session(accessMode).flatMap { s =>
      sessionWork(s).flatMap { v =>
        s.close().map(_ => v)
      } recoverWith {
        case ex: Throwable =>
          s.close().flatMap(_ => F.failed(ex))
      }
    }

  def close(): F[Unit] =
    F.async { cb =>
      driver
        .closeAsync()
        .accept { _: Void => cb(Right(())) }
        .recover { ex: Throwable => cb(Left(ex)) }
    }
}
