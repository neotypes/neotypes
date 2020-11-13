package neotypes

import internal.syntax.async._
import internal.syntax.stage._

import org.neo4j.driver.{AccessMode, Driver => NeoDriver, SessionConfig}

/** A neotypes driver for accessing the neo4j graph database
  * A driver wrapped in the resource type can be created using the neotypes GraphDatabase
  * {{{
  *    val driver = GraphDatabase[F]("bolt://localhost:7687").driver
  * }}}
  *
  * @tparam F effect type for driver
  *
  * @define futinfo When your effect type is scala.Future there is no concept of Resource. For more information see <a href = https://neotypes.github.io/neotypes/docs/alternative_effects.html>alternative effects</a>
  */
final class Driver[F[_]] private[neotypes] (private val driver: NeoDriver) {
  /** Acquire an async session to the database with the default config.
    * @note $futinfo
    *
    * @param F asynchronous effect type with resource type defined.
    * @tparam R resource type dependant on the effect type F.
    * @return a resource with the [[Session]].
    *
    * @see [[SessionConfig]].
    */
  def session[R[_]](implicit F: Async.Aux[F, R]): R[Session[F]] =
    session(SessionConfig.defaultConfig)

  /** Acquire an async session to the database.
    * @note $futinfo
    *
    * @param config the [[SessionConfig]] used to initialize the session.
    * @param F asynchronous effect type with resource type defined.
    * @tparam R resource type dependant on the effect type F.
    * @return a resource with the [[Session]].
    */
  def session[R[_]](config: SessionConfig)
                   (implicit F: Async.Aux[F, R]): R[Session[F]] =
    F.resource(createSession(config))(session => session.close)

  /** Apply a unit of work to a read session.
    * @note ensures the session is properly closed after its use.
    *
    * @param sessionWork a function that takes a Session[F] and returns an F[T].
    * @param F the effect type.
    * @tparam T the type of the value that will be returned when the work is executed.
    * @return an effectual value that will compute the result of the function.
    */
  def readSession[T](sessionWork: Session[F] => F[T])
                    (implicit F: Async[F]): F[T] =
    withSession(AccessMode.READ)(sessionWork)

  /** Apply a unit of work to a write session.
    * @note ensures the session is properly closed after its use.
    *
    * @param sessionWork a function that takes a Session[F] and returns an F[T].
    * @param F the effect type.
    * @tparam T the type of the value that will be returned when the work is executed.
    * @return an effectual value that will compute the result of the function.
    */
  def writeSession[T](sessionWork: Session[F] => F[T])
                     (implicit F: Async[F]): F[T] =
    withSession(AccessMode.WRITE)(sessionWork)

  private[this] def withSession[T](accessMode: AccessMode)
                                  (sessionWork: Session[F] => F[T])
                                  (implicit F: Async[F]): F[T] = {
    val config =  SessionConfig.builder.withDefaultAccessMode(accessMode).build()
    createSession(config).guarantee(sessionWork) {
      case (session, _) => session.close
    }
  }

  private[this] def createSession(config: SessionConfig)
                                 (implicit F: Async[F]): F[Session[F]] =
    F.makeLock.flatMap { lock =>
      F.delay {
        Session(F, driver.asyncSession(config))(lock)
      }
    }

  /** Acquire a streaming session to the database with the default config.
    * @note ensures the session is properly closed after its use.
    *
    * @param F asynchronous effect type.
    * @tparam S streaming type dependant on the effect type F.
    * @return a single element streaming with the [[StreamingSession]].
    *
    * @see [[SessionConfig]].
    */
  def streamingSession[S[_]](implicit F: Async[F], S: Stream.Aux[S, F]): S[StreamingSession[F, S]] =
    streamingSession(SessionConfig.defaultConfig)

  /** Acquire a streaming session to the database.
    * @note ensures the session is properly closed after its use.
    *
    * @param config the [[SessionConfig]] used to initialize the session.
    * @param F asynchronous effect type.
    * @tparam S streaming type dependant on the effect type F.
    * @return a single element streaming with the [[StreamingSession]].
    */
  def streamingSession[S[_]](config: SessionConfig)
                            (implicit F: Async[F], S: Stream.Aux[S, F]): S[StreamingSession[F, S]] = {
    val session = F.makeLock.flatMap { lock =>
      F.delay {
        Session(F, S, driver.rxSession(config))(lock)
      }
    }
    S.resource(session)((s, _) => s.close)
  }

  /** Close the resources assigned by the neo4j driver
    *
    * @param F the effect type
    * @return an effect F of Unit
    */
  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      driver.closeAsync().acceptVoid(cb)
    }
}
