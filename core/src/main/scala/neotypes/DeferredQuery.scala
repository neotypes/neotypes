package neotypes

import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import scala.collection.compat.Factory
import scala.collection.mutable.StringBuilder

/** Represents a Neo4j query that can be asynchronously on a effect type.
  *
  * @see <a href="https://neotypes.github.io/neotypes/parameterized_queries.html">The parametrized queries documentation</a>.
  *
  * @param query statement that will be executed
  * @param params variable values substituted into the query statement
  * @tparam T the type of the value that will be returned when the query is executed.
  */
final case class DeferredQuery[T] private[neotypes] (query: String, params: Map[String, QueryParam]) {
  import DeferredQuery._

  /** Executes the query and returns a List of values.
    *
    * @example
    * {{{
    * val result: F[List[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .list(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  def list[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                (implicit rm: ResultMapper[T]): F[List[T]] =
    driver.transact(config)(tx => list(tx))

  /** Executes the query and returns a Map[K,V] of values.
    *
    * @example
    * {{{
    * val result: F[Map[Person, Movie]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .map(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param ev evidence that T is a tuple (K, V).
    * @param rm result mapper for type (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value pairs.
    */
  def map[F[_], K, V](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    driver.transact(config)(tx => map(tx))

  /** Executes the query and returns a Set of values.
    *
    * @example
    * {{{
    * val result: F[Set[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .set(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  def set[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    driver.transact(config)(tx => set(tx))

  /** Executes the query and returns a Vector of values.
    *
    * @example
    * {{{
    * val result: F[Vector[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .vector(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  def vector[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    driver.transact(config)(tx => vector(tx))

  /** Executes the query and returns the unique record in the result.
    *
    * @example
    * {{{
    * val result: F[(Person, Movie)] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .single(driver)
    * }}}
    *
    * @note This will fail if the query returned more than a single result.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  def single[F[_]](driver: Driver[F],config: TransactionConfig = TransactionConfig.default)
                  (implicit rm: ResultMapper[T]): F[T] =
    driver.transact(config)(tx => single(tx))

  /** Evaluate the query an get the results as a Stream.
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T values.
    */
  def stream[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig = TransactionConfig.default)
                        (implicit rm: ResultMapper[T]): S[T] =
    driver.streamingTransact(config)(tx => stream(tx))

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *   .query[Unit]
    *   .execute(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config (optional).
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def execute[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                   (implicit em: ExecutionMapper[T]): F[T] =
    driver.transact(config)(tx => execute(tx))

  /** Executes the query and returns a List of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)]
    *
    * val result: F[List[(Person, Movie)]] =
    *   driver.transact(tx => deferredQuery.list(tx))
    * }}}
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  def list[F[_]](tx: Transaction[F])
                (implicit rm: ResultMapper[T]): F[List[T]] =
    tx.list(query, params)

  /** Executes the query and returns a Map of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)]
    *
    * val result: F[Map[Person, Movie]] =
    *   driver.transact(tx => deferredQuery.map(tx))
    * }}}
    *
    * @param tx neotypes transaction.
    * @param ev evidence that T is a tuple (K, V).
    * @param rm result mapper for type (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value pairs.
    */
  def map[F[_], K, V](tx: Transaction[F])
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] = {
    internal.utils.void(ev)
    tx.map(query, params)
  }

  /** Executes the query and returns a Set of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)]
    *
    * val result: F[Set[(Person, Movie)]] =
    *   driver.transact(tx => deferredQuery.set(tx))
    * }}}
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  def set[F[_]](tx: Transaction[F])
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    tx.set(query, params)

  /** Executes the query and returns a Vector of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)]
    *
    * val result: F[Vector[(Person, Movie)]] =
    *   driver.transact(tx => deferredQuery.vector(tx))
    * }}}
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  def vector[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    tx.vector(query, params)

  /** Executes the query and returns the unique record in the result.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m".query[(Person, Movie)]
    *
    * val result: F[(Person, Movie)] =
    *   driver.transact(tx => deferredQuery.single(tx))
    * }}}
    *
    * @note This will fail if the query returned more than a single result.
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  def single[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *
    * driver.transact(tx => deferredQuery.execute(tx))
    * }}}
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def execute[F[_]](tx: Transaction[F])
                   (implicit em: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)

  /** Evaluate the query an get the results as a Stream.
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T values.
    */
  def stream[S[_], F[_]](tx: StreamingTransaction[S, F])
                        (implicit rm: ResultMapper[T]): S[T] =
    tx.stream(query, params)

  /** Executes the query and returns a custom collection of values.
    *
    * @example
    * {{{
    * val result: F[ListMap[Person, Movie]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .collectAs(ListMap)(driver)
    * }}}
    *
    * @param factory the type of the custom collection to return.
    * @tparam C collection type to be constructed.
    * @return An effectual value that will compute a Collection of T elements.
    */
  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory -> this)

  /** Creates a new query with an updated set of parameters.
   *
    * @note If params contains a key that is already present in the current query,
    * the new one will override the previous one.
    *
    * @param params QueryParams to be added.
    * @return DeferredQuery with params added to existing params.
    */
  def withParams(params: Map[String, QueryParam]): DeferredQuery[T] =
    this.copy(params = this.params ++ params)
}

private[neotypes] object DeferredQuery {
  private[neotypes] final class CollectAsPartiallyApplied[T, C](private val factoryAndDq: (Factory[T, C], DeferredQuery[T])) extends AnyVal {
    def apply[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                   (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      driver.transact(config)(tx => tx.collectAs(factory)(dq.query, dq.params))
    }

    def apply[F[_]](tx: Transaction[F])
                   (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      tx.collectAs(factory)(dq.query, dq.params)
    }
  }
}

/** A builder for constructing instance of [DeferredQuery].
  *
  * The idiomatic way to use the DeferredQueryBuilder is with the `c` String Interpolation.
  *
  * @see <a href="https://neotypes.github.io/neotypes/parameterized_queries.html">The parametrized queries documentation</a>.
  *
  * @example
  * {{{
  * val name = "John"
  * val born = 1980
  *
  * val deferredQueryBuilder = c"create (a: Person { name: \$name," + c"born: \$born })"
  *
  * val deferredQuery = deferredQueryBuilder.query[Unit]
  * }}}
  */
final class DeferredQueryBuilder private[neotypes] (private val parts: List[DeferredQueryBuilder.Part]) {
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query}

  /** Creates a [DeferredQuery] from this builder.
    *
    * @tparam T the result type of the constructed query.
    * @return A [DeferredQuery[T]]
    */
  def query[T]: DeferredQuery[T] = {
    @annotation.tailrec
    def loop(remaining: List[Part], queryBuilder: StringBuilder, accParams: Map[String, QueryParam], nextParamIdx: Int): DeferredQuery[T] =
      remaining match {
        case Nil =>
          DeferredQuery(
            query  = queryBuilder.mkString,
            params = accParams
          )

        case Query(query1) :: Query(query2) :: xs =>
          loop(
            remaining = Query(query2) :: xs,
            queryBuilder.append(query1).append(" "),
            accParams,
            nextParamIdx
          )

        case Query(query) :: xs =>
          loop(
            remaining = xs,
            queryBuilder.append(query),
            accParams,
            nextParamIdx
          )

        case Param(param) :: xs =>
          val paramName = s"${PARAMETER_NAME_PREFIX}${nextParamIdx}"
          loop(
            remaining = xs,
            queryBuilder.append("$").append(paramName),
            accParams + (paramName -> param),
            nextParamIdx + 1
          )
      }

      loop(
        remaining = this.parts,
        new StringBuilder(),
        accParams = Map.empty,
        nextParamIdx = 1
      )
  }

  /** Concatenate another [DeferredQueryBuilder] with this one.
    *
    * @param that [DeferredQueryBuilder] to be concatenated.
    * @return A new [DeferredQueryBuilder].
    */
  def +(that: DeferredQueryBuilder): DeferredQueryBuilder =
    new DeferredQueryBuilder(this.parts ::: that.parts)

  /** Concatenate a [String] with this [DeferredQueryBuilder].
    *
    * @param that [String] to be concatenated.
    * @return A new [DeferredQueryBuilder].
    */
  def +(that: String): DeferredQueryBuilder =
    new DeferredQueryBuilder(this.parts :+ Query(that))
}

private[neotypes] object DeferredQueryBuilder {
  final val PARAMETER_NAME_PREFIX: String = "p"

  sealed trait Part extends Product with Serializable

  final case class Query(part: String) extends Part

  final case class Param(value: QueryParam) extends Part
}
