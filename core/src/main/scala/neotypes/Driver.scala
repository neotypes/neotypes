package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.v1.{AccessMode, Driver => NDriver}

import scala.jdk.CollectionConverters._
import scala.language.higherKinds

final class Driver[F[_]](private val driver: NDriver) extends AnyVal {
  def session[R[_]](implicit F: Async.Aux[F, R]): R[Session[F]] =
    session[R](accessMode = AccessMode.READ)

  def session[R[_]](accessMode: AccessMode, bookmarks: String*)
                   (implicit F: Async.Aux[F, R]): R[Session[F]] =
    F.resource(createSession(accessMode, bookmarks))(session => session.close)

  private[this] def createSession(accessMode: AccessMode, bookmarks: Seq[String] = Seq.empty): Session[F] =
    new Session(
      bookmarks match {
        case Seq()         => driver.session(accessMode)
        case Seq(bookmark) => driver.session(accessMode, bookmark)
        case _             => driver.session(accessMode, bookmarks.asJava)
      }
    )

  def readSession[T](sessionWork: Session[F] => F[T])
                    (implicit F: Async[F]): F[T] =
    withSession(AccessMode.READ)(sessionWork)

  def writeSession[T](sessionWork: Session[F] => F[T])
                     (implicit F: Async[F]): F[T] =
    withSession(AccessMode.WRITE)(sessionWork)

  private[this] def withSession[T](accessMode: AccessMode)
                                  (sessionWork: Session[F] => F[T])
                                  (implicit F: Async[F]): F[T] =
    F.delay(createSession(accessMode)).guarantee(sessionWork) {
      case (session, _) => session.close
    }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      driver.closeAsync().acceptVoid(cb)
    }
}
