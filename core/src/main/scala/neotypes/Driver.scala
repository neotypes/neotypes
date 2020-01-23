package neotypes

import internal.syntax.async._
import internal.syntax.stage._
import org.neo4j.driver.v1.{AccessMode, Driver => NDriver}

import scala.jdk.CollectionConverters._

final class Driver[F[_]](private val driver: NDriver) extends AnyVal {

  private[this] def createSession(accessMode: AccessMode, bookmarks: Seq[String] = Seq.empty): Session[F] =
    new Session(
      bookmarks match {
        case Seq()         => driver.session(accessMode)
        case Seq(bookmark) => driver.session(accessMode, bookmark)
        case _             => driver.session(accessMode, bookmarks.asJava)
      }
    )

  def readTransaction[T](sessionWork: Transaction[F] => F[T])
                        (implicit F: Async[F]): F[T] =
    withSession(AccessMode.READ)(sessionWork)


  def writeTransaction[T](sessionWork: Transaction[F] => F[T])
                         (implicit F: Async[F]): F[T] =
    withSession(AccessMode.WRITE)(sessionWork)

  private[this] def withSession[T](accessMode: AccessMode)
                                  (sessionWork: Transaction[F] => F[T])
                                  (implicit F: Async[F]): F[T] = {
    F.delay(createSession(accessMode)).flatMap{sess => sess.transaction.guarantee(sessionWork) {
      case (transaction, None) =>
        transaction.commit.flatMap(_ => sess.close)
      case (transaction, Some(_)) =>
        transaction.rollback.flatMap(_ => sess.close)
    }
    }
  }

  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      driver.closeAsync().acceptVoid(cb)
    }
}
