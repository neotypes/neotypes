package neotypes

import java.net.URI

import org.neo4j.driver.{AuthToken, AuthTokens, Config, Driver => NDriver, GraphDatabase => NFactory}

import scala.jdk.CollectionConverters._

/** Factory of Drivers. */
object GraphDatabase {
  def driver[F[_]]: DriverPartiallyApplied[F] =
    new DriverPartiallyApplied(dummy = true)

  private[neotypes] final class DriverPartiallyApplied[F[_]](private val dummy: Boolean) extends AnyVal {
    /** Creates a new Driver using the provided uri,
      * without authentication and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token,
      * and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authToken: AuthToken)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & config,
      * and with out authentication.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, config: Config)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, AuthTokens.none(), config)


    /** Creates a new Driver using the provided uri, authentication token & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authToken: AuthToken, config: Config)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      create(
        NFactory.driver(URI.create(uri), authToken, config)
      )

    /** Creates a new Driver using the provided uri,
      * without authentication and with the default configuration.
      */
    def apply[R[_]](uri: URI)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token,
      * and with the default configuration.
      */
    def apply[R[_]](uri: URI, authToken: AuthToken)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri,
      * and without authentication.
      */
    def apply[R[_]](uri: URI, config: Config)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token & configuration. */
    def apply[R[_]](uri: URI, authToken: AuthToken, config: Config)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      create(
        NFactory.driver(uri, authToken, config)
      )

    /** Creates a new routing Driver using the provided uris, authentication token & configuration. */
    def apply[R[_]](routingUris: Seq[URI], authToken: AuthToken, config: Config)
                   (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      create(
        NFactory.routingDriver(routingUris.asJava, authToken, config)
      )

    private def create[R[_]](neoDriver: => NDriver)
                            (implicit F: Async.Aux[F, R]): R[Driver[F]] =
      F.resource(Driver[F](neoDriver))(_.close)
  }

  def streamingDriver[F[_]]: StreamingDriverPartiallyApplied[F] =
    new StreamingDriverPartiallyApplied(dummy = true)

  private[neotypes] final class StreamingDriverPartiallyApplied[S[_]](private val dummy: Boolean) extends AnyVal {
    /** Creates a new Driver using the provided uri,
      * without authentication and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](uri: String)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token,
      * and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](uri: String, authToken: AuthToken)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & config,
      * and with out authentication.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](uri: String, config: Config)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, AuthTokens.none(), config)


    /** Creates a new Driver using the provided uri, authentication token & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](uri: String, authToken: AuthToken, config: Config)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      create(
        NFactory.driver(URI.create(uri), authToken, config)
      )

    /** Creates a new Driver using the provided uri,
      * without authentication and with the default configuration.
      */
    def apply[F[_], R[_]](uri: URI)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token,
      * and with the default configuration.
      */
    def apply[F[_], R[_]](uri: URI, authToken: AuthToken)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri,
      * and without authentication.
      */
    def apply[F[_], R[_]](uri: URI, config: Config)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token & configuration. */
    def apply[F[_], R[_]](uri: URI, authToken: AuthToken, config: Config)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      create(
        NFactory.driver(uri, authToken, config)
      )

    /** Creates a new routing Driver using the provided uris, authentication token & configuration. */
    def apply[F[_], R[_]](routingUris: Seq[URI], authToken: AuthToken, config: Config)
                         (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      create(
        NFactory.routingDriver(routingUris.asJava, authToken, config)
      )

    private def create[F[_], R[_]](neoDriver: => NDriver)
                            (implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamingDriver[S, F]] =
      F.resource(Driver[S, F](neoDriver))(_.close)
  }
}
