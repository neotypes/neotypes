package neotypes

import java.net.URI

import org.neo4j.driver.v1.{AuthToken, AuthTokens, Config, GraphDatabase => Factory}

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.{Try, Success, Failure}

/** Factory of Drivers. */
object GraphDatabase {
  /** Creates a new Driver using the provided uri,
    * without authentication and with the default configuration.
    *
    * Note: Fails if the given string is not a valid uri.
    */
  def driver[F[_]: Async](uri: String): F[Driver[F]] =
    driver(uri, AuthTokens.none(), Config.defaultConfig())

  /** Creates a new Driver using the provided uri & authentication token,
    * and with the default configuration.
    *
    * Note: Fails if the given string is not a valid uri.
    */
  def driver[F[_]: Async](uri: String, authToken: AuthToken): F[Driver[F]] =
    driver(uri, authToken, Config.defaultConfig())

  /** Creates a new Driver using the provided uri & config,
    * and with out authentication.
    *
    * Note: Fails if the given string is not a valid uri.
    */
  def driver[F[_]: Async](uri: String, config: Config): F[Driver[F]] =
    driver(uri, AuthTokens.none(), config)


  /** Creates a new Driver using the provided uri, authentication token & configuration.
    *
    * Note: Fails if the given string is not a valid uri.
    */
  def driver[F[_]](uri: String, authToken: AuthToken, config: Config)
                  (implicit F: Async[F]): F[Driver[F]] =
    F.suspend {
      Try(URI.create(uri)) match {
        case Success(uri) =>
          driver(uri, authToken, config)

        case Failure(exception) =>
          F.failed(exception)
      }
    }

  /** Creates a new Driver using the provided uri,
    * without authentication and with the default configuration.
    */
  def driver[F[_]: Async](uri: URI): F[Driver[F]] =
    driver(uri, AuthTokens.none(), Config.defaultConfig())

  /** Creates a new Driver using the provided uri & authentication token,
    * and with the default configuration.
    */
  def driver[F[_]: Async](uri: URI, authToken: AuthToken): F[Driver[F]] =
    driver(uri, authToken, Config.defaultConfig())

  /** Creates a new Driver using the provided uri,
    * and without authentication.
    */
  def driver[F[_]: Async](uri: URI, config: Config): F[Driver[F]] =
    driver(uri, AuthTokens.none(), config)

  /** Creates a new Driver using the provided uri, authentication token & configuration. */
  def driver[F[_]](uri: URI, authToken: AuthToken, config: Config)
                  (implicit F: Async[F]): F[Driver[F]] =
    F.delay(new Driver(Factory.driver(uri, authToken, config)))

  /** Creates a new routing Driver using the provided uris, authentication token & configuration. */
  def routingDriver[F[_]](routingUris: Seq[URI], authToken: AuthToken, config: Config)
                         (implicit F: Async[F]): F[Driver[F]] =
    F.delay(new Driver(Factory.routingDriver(routingUris.asJava, authToken, config)))
}
