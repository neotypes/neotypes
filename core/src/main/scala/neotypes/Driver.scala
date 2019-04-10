package neotypes

import neotypes.implicits._
import neotypes.utils.stage._
import org.neo4j.driver.v1.{AccessMode, Driver => NDriver, Session => NSession}

final class Driver[F[_]](driver: NDriver)(implicit F: Async[F]) {
  def readSession[T](sessionWork: Session[F] => F[T]): F[T] =
    session(driver.session(AccessMode.READ))(sessionWork)

  def writeSession[T](sessionWork: Session[F] => F[T]): F[T] =
    session(driver.session(AccessMode.WRITE))(sessionWork)

  private[this] def session[T](session: NSession)(sessionWork: Session[F] => F[T]): F[T] = {
    val s = new Session[F](session)
    sessionWork(s).flatMap { v =>
      s.close().map(_ => v)
    }.recoverWith {
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
