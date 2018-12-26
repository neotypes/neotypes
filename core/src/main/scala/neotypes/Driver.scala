package neotypes

import org.neo4j.driver.v1.{AccessMode, Driver => NDriver, Session => NSession}
import Async._
import neotypes.utils.CompletionUtils.exceptionally
import neotypes.utils.FunctionUtils._

class Driver[F[_]](driver: NDriver)(implicit F: Async[F]) {

  def readSession[T](sessionWork: Session[F] => F[T]): F[T] = {
    session(driver.session(AccessMode.READ))(sessionWork)
  }

  def writeSession[T](sessionWork: Session[F] => F[T]): F[T] = {
    session(driver.session(AccessMode.WRITE))(sessionWork)
  }

  private[this] def session[T](session: NSession)(sessionWork: Session[F] => F[T]): F[T] = {
    val s = new Session[F](session)
    sessionWork(s).flatMap { v =>
      s.close().map(_ => v)
    }.recoverWith {
      case ex: Throwable =>
        s.close().flatMap(_ => implicitly[Async[F]].failed(ex))
    }
  }

  def close(): F[Unit] = F.async(cb =>
    driver.closeAsync()
      .thenAccept { _: Void => cb(Right(())) }
      .exceptionally(exceptionally(ex => cb(Left(ex))))
  )
}
