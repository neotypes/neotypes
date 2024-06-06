package neotypes

import java.net.URI
import org.neo4j.driver.{AuthToken, AuthTokenManager, AuthTokens, Config, Driver => NDriver, GraphDatabase => NFactory}

/** Factory of Drivers. */
object GraphDatabase {
  def asyncDriver[F[_]]: AsyncDriverPartiallyApplied[F] =
    new AsyncDriverPartiallyApplied(dummy = true)

  private[neotypes] final class AsyncDriverPartiallyApplied[F[_]](private val dummy: Boolean) extends AnyVal {

    /** Creates a new Driver using the provided uri, without authentication and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token, and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authToken: AuthToken)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token manager, and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authTokenManager: AuthTokenManager)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, authTokenManager, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & config, and with out authentication.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, config: Config)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token manager & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authToken: AuthToken, config: Config)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      create(NFactory.driver(URI.create(uri), authToken, config))

    /** Creates a new Driver using the provided uri, authentication token & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[R[_]](uri: String, authTokenManager: AuthTokenManager, config: Config)(implicit
      F: Async.Aux[F, R]
    ): R[AsyncDriver[F]] =
      create(NFactory.driver(URI.create(uri), authTokenManager, config))

    /** Creates a new Driver using the provided uri, without authentication and with the default configuration.
      */
    def apply[R[_]](uri: URI)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token, and with the default configuration.
      */
    def apply[R[_]](uri: URI, authToken: AuthToken)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token manager, and with the default configuration.
      */
    def apply[R[_]](uri: URI, authTokenManager: AuthTokenManager)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, authTokenManager, Config.defaultConfig())

    /** Creates a new Driver using the provided uri, and without authentication.
      */
    def apply[R[_]](uri: URI, config: Config)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token & configuration. */
    def apply[R[_]](uri: URI, authToken: AuthToken, config: Config)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      create(NFactory.driver(uri, authToken, config))

    /** Creates a new Driver using the provided uri, authentication token manager& configuration. */
    def apply[R[_]](uri: URI, authTokenManager: AuthTokenManager, config: Config)(implicit
      F: Async.Aux[F, R]
    ): R[AsyncDriver[F]] =
      create(NFactory.driver(uri, authTokenManager, config))

    private def create[R[_]](neoDriver: => NDriver)(implicit F: Async.Aux[F, R]): R[AsyncDriver[F]] =
      F.resource(Driver.async[F](neoDriver))(_.close)
  }

  def streamDriver[F[_]]: StreamDriverPartiallyApplied[F] =
    new StreamDriverPartiallyApplied(dummy = true)

  private[neotypes] final class StreamDriverPartiallyApplied[S[_]](private val dummy: Boolean) extends AnyVal {

    /** Creates a new Driver using the provided uri, without authentication and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](uri: String)(implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamDriver[S, F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token, and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](
      uri: String,
      authToken: AuthToken
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token manager, and with the default configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](
      uri: String,
      authTokenManager: AuthTokenManager
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, authTokenManager, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & config, and with out authentication.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](
      uri: String,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](
      uri: String,
      authToken: AuthToken,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      create(NFactory.driver(URI.create(uri), authToken, config))

    /** Creates a new Driver using the provided uri, authentication token manager & configuration.
      *
      * Note: Fails if the given string is not a valid uri.
      */
    def apply[F[_], R[_]](
      uri: String,
      authTokenManager: AuthTokenManager,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      create(NFactory.driver(URI.create(uri), authTokenManager, config))

    /** Creates a new Driver using the provided uri, without authentication and with the default configuration.
      */
    def apply[F[_], R[_]](uri: URI)(implicit S: Stream.Aux[S, F], F: Async.Aux[F, R]): R[StreamDriver[S, F]] =
      apply(uri, AuthTokens.none(), Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token, and with the default configuration.
      */
    def apply[F[_], R[_]](
      uri: URI,
      authToken: AuthToken
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, authToken, Config.defaultConfig())

    /** Creates a new Driver using the provided uri & authentication token manager, and with the default configuration.
      */
    def apply[F[_], R[_]](
      uri: URI,
      authTokenManager: AuthTokenManager
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, authTokenManager, Config.defaultConfig())

    /** Creates a new Driver using the provided uri, and without authentication.
      */
    def apply[F[_], R[_]](
      uri: URI,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      apply(uri, AuthTokens.none(), config)

    /** Creates a new Driver using the provided uri, authentication token & configuration. */
    def apply[F[_], R[_]](
      uri: URI,
      authToken: AuthToken,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      create(NFactory.driver(uri, authToken, config))

    /** Creates a new Driver using the provided uri, authentication token manager & configuration. */
    def apply[F[_], R[_]](
      uri: URI,
      authTokenManager: AuthTokenManager,
      config: Config
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      create(NFactory.driver(uri, authTokenManager, config))

    private def create[F[_], R[_]](
      neoDriver: => NDriver
    )(implicit
      S: Stream.Aux[S, F],
      F: Async.Aux[F, R]
    ): R[StreamDriver[S, F]] =
      F.resource(Driver.stream[S, F](neoDriver))(_.close)
  }
}
