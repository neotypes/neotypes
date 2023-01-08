package neotypes

import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import scala.collection.compat.Factory
import scala.collection.mutable.StringBuilder

private[neotypes] sealed trait BaseDeferredQuery[T] {
  import BaseDeferredQuery.BaseCollectAsPartiallyApplied

  /** The query statement that will be executed. */
  def query: String

  /** The values substituted into the query statement. */
  def params: Map[String, QueryParam]

  /** Creates a new query with an updated set of parameters.
    *
    * @note If params contains a key that is already present in the current query,
    * the new one will override the previous one.
    *
    * @param params QueryParams to be added.
    * @return DeferredQuery with params added to existing params.
    */
  def withParams(params: Map[String, QueryParam]): BaseDeferredQuery[T]

  protected def effectDriverTransaction[F[_], O](driver: Driver[F])
                                                (tx: Transaction[F] => F[O]): F[O]
  protected def effectDriverTransactionConfig[F[_], O](driver: Driver[F], config: TransactionConfig)
                                                      (tx: Transaction[F] => F[O]): F[O]
  protected def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])
                                                   (st: StreamingTransaction[S, F] => S[T]): S[T]
  protected def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                                                         (st: StreamingTransaction[S, F] => S[T]): S[T]

  def collectAs[C](factory: Factory[T, C]): BaseCollectAsPartiallyApplied[T, C]

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
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  final def list[F[_], O](driver: Driver[F])
                         (implicit rm: ResultMapper[T]): F[List[T]] =
    effectDriverTransaction(driver)(tx => tx.list(query, params))

  /** Executes the query and returns a List of values.
    *
    * @example
    * {{{
    * val result: F[List[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .list(driver, myConfig)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  final def list[F[_], C[_]](driver: Driver[F], config: TransactionConfig)
                            (implicit rm: ResultMapper[T]): F[List[T]] =
    effectDriverTransactionConfig(driver, config)(tx => tx.list(query, params))

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
    * @param ev evidence that T is a tuple (K, V).
    * @param rm result mapper for type (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value pairs.
    */
  final def map[F[_], O, K, V](driver: Driver[F])
                              (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] = {
    internal.utils.void(ev)
    effectDriverTransaction(driver)(tx => tx.map(query, params))
  }

  /** Executes the query and returns a Map[K,V] of values.
    *
    * @example
    * {{{
    * val result: F[Map[Person, Movie]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .map(driver, myConfig)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param ev evidence that T is a tuple (K, V).
    * @param rm result mapper for type (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value pairs.
    */
  final def map[F[_], O, K, V](driver: Driver[F], config: TransactionConfig)
                              (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] = {
    internal.utils.void(ev)
    effectDriverTransactionConfig(driver, config)(tx => tx.map(query, params))
  }

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
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  final def set[F[_]](driver: Driver[F])
                     (implicit rm: ResultMapper[T]): F[Set[T]] =
    effectDriverTransaction(driver)(tx => tx.set(query, params))

  /** Executes the query and returns a Set of values.
    *
    * @example
    * {{{
    * val result: F[Set[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .set(driver, myConfig)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  final def set[F[_]](driver: Driver[F], config: TransactionConfig)
                     (implicit rm: ResultMapper[T]): F[Set[T]] =
    effectDriverTransactionConfig(driver, config)(tx => tx.set(query, params))

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
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  final def vector[F[_]](driver: Driver[F])
                        (implicit rm: ResultMapper[T]): F[Vector[T]] =
    effectDriverTransaction(driver)(tx => tx.vector(query, params))

  /** Executes the query and returns a Vector of values.
    *
    * @example
    * {{{
    * val result: F[Vector[(Person, Movie)]] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .vector(driver, myConfig)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  final def vector[F[_]](driver: Driver[F], config: TransactionConfig)
                        (implicit rm: ResultMapper[T]): F[Vector[T]] =
    effectDriverTransactionConfig(driver, config)(tx => tx.vector(query, params))

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
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  final def single[F[_]](driver: Driver[F])
                        (implicit rm: ResultMapper[T]): F[T] =
    effectDriverTransaction(driver)(tx => tx.single(query, params))

  /** Executes the query and returns the unique record in the result.
    *
    * @example
    * {{{
    * val result: F[(Person, Movie)] =
    *   "MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *     .query[(Person, Movie)]
    *     .single(driver, myConfig)
    * }}}
    *
    * @note This will fail if the query returned more than a single result.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param rm result mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  final def single[F[_]](driver: Driver[F],config: TransactionConfig)
                        (implicit rm: ResultMapper[T]): F[T] =
    effectDriverTransactionConfig(driver, config)(tx => tx.single(query, params))

  /** Evaluate the query an get the results as a Stream.
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param driver neotypes driver.
    * @param rm result mapper for type T.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T values.
    */
  final def stream[S[_], F[_]](driver: StreamingDriver[S, F])
                              (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransaction(driver)(tx => tx.stream(query, params))

  /** Evaluate the query an get the results as a Stream.
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param rm result mapper for type T.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T values.
    */
  final def stream[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                              (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransactionConfig(driver, config)(tx => tx.stream(query, params))
}

private[neotypes] object BaseDeferredQuery {
  private[neotypes] sealed trait BaseCollectAsPartiallyApplied[T, C] extends Any {
    def apply[F[_]](driver: Driver[F])
                   (implicit rm: ResultMapper[T]): F[C]

    def apply[F[_]](driver: Driver[F], config: TransactionConfig)
                   (implicit rm: ResultMapper[T]): F[C]
  }

  private[neotypes] final class CollectAsPartiallyApplied[T, C](
      private val factoryAndDq: (Factory[T, C], BaseDeferredQuery[T])
  ) extends AnyVal with BaseCollectAsPartiallyApplied[T, C] {
    override def apply[F[_]](driver: Driver[F])
                            (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      driver.transact(tx => tx.collectAs(factory)(dq.query, dq.params))
    }

    override def apply[F[_]](driver: Driver[F], config: TransactionConfig)
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

  private[neotypes] final class ReadOnlyCollectAsPartiallyApplied[T, C](
      private val factoryAndDq: (Factory[T, C], BaseDeferredQuery[T])
  ) extends AnyVal with BaseCollectAsPartiallyApplied[T, C] {
    override def apply[F[_]](driver: Driver[F])
                            (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      driver.readOnlyTransact(tx => tx.collectAs(factory)(dq.query, dq.params))
    }

    override def apply[F[_]](driver: Driver[F], config: TransactionConfig)
                            (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      driver.readOnlyTransact(config)(tx => tx.collectAs(factory)(dq.query, dq.params))
    }
  }
}

/** Represents a Neo4j query that can be asynchronously on a effect type.
  *
  * @tparam T the type of the value that will be returned when the query is executed.
  *
  * @see <a href="https://neotypes.github.io/neotypes/parameterized_queries.html">The parametrized queries documentation</a>.
  */
final case class DeferredQuery[T](
    override final val query: String,
    override final val params: Map[String, QueryParam]
) extends BaseDeferredQuery[T] {
  import BaseDeferredQuery.CollectAsPartiallyApplied

  override def effectDriverTransaction[F[_], O](driver: Driver[F])
                                               (tx: Transaction[F] => F[O]): F[O] =
    driver.transact(tx)

  override def effectDriverTransactionConfig[F[_], O](driver: Driver[F], config: TransactionConfig)
                                                     (tx: Transaction[F] => F[O]): F[O] =
    driver.transact(config)(tx)

  override def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])
                                                  (st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransact(st)

  override def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                                                        (st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransact(config)(st)

  override def withParams(params: Map[String, QueryParam]): DeferredQuery[T] =
    this.copy(params = this.params ++ params)

  override def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory -> this)

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
    * @param em execution mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  final def execute[F[_]](driver: Driver[F])
                         (implicit em: ExecutionMapper[T]): F[T] =
    effectDriverTransaction(driver)(tx => execute(tx))

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *   .query[Unit]
    *   .execute(driver, myConfig)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param em execution mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  final def execute[F[_]](driver: Driver[F], config: TransactionConfig)
                         (implicit em: ExecutionMapper[T]): F[T] =
    effectDriverTransactionConfig(driver, config)(tx => execute(tx))

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
  final def list[F[_]](tx: Transaction[F])
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
  final def map[F[_], K, V](tx: Transaction[F])
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
  final def set[F[_]](tx: Transaction[F])
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
  final def vector[F[_]](tx: Transaction[F])
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
  final def single[F[_]](tx: Transaction[F])
                        (implicit rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  /** Evaluate the query and get the results as a Stream.
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param tx neotypes transaction.
    * @param rm result mapper for type T.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T values.
    */
  final def stream[S[_], F[_]](tx: StreamingTransaction[S, F])
                              (implicit rm: ResultMapper[T]): S[T] =
    tx.stream(query, params)

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
    * @param em execution mapper for type T.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  final def execute[F[_]](tx: Transaction[F])
                         (implicit em: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)
}

/** A special case of [[DeferredQuery]] that only admits read-only operations. */
final case class ReadOnlyDeferredQuery[T](
    override final val query: String,
    override final val params: Map[String, QueryParam]
) extends BaseDeferredQuery[T] {
  import BaseDeferredQuery.ReadOnlyCollectAsPartiallyApplied

  override def effectDriverTransaction[F[_], O](driver: Driver[F])
                                               (tx: Transaction[F] => F[O]): F[O] =
    driver.readOnlyTransact(tx)

  override def effectDriverTransactionConfig[F[_], O](driver: Driver[F], config: TransactionConfig)
                                                     (tx: Transaction[F] => F[O]): F[O] =
    driver.readOnlyTransact(config)(tx)

  override def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])
                                                  (st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.readOnlyStreamingTransact(st)

  override def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                                                        (st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.readOnlyStreamingTransact(config)(st)

  override def withParams(params: Map[String, QueryParam]): ReadOnlyDeferredQuery[T] =
    this.copy(params = this.params ++ params)

  override def collectAs[C](factory: Factory[T, C]): ReadOnlyCollectAsPartiallyApplied[T, C] =
    new ReadOnlyCollectAsPartiallyApplied(factory -> this)
}

/** A builder for constructing instance of [[DeferredQuery]].
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
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query, SubQueryParam}

  def query[T <: Any]: DeferredQuery[T] = {
    val (query, params, _) = build()
    DeferredQuery(query, params)
  }

  def readOnlyQuery[T <: Any]: ReadOnlyDeferredQuery[T] = {
    val (query, params, _) = build()
    ReadOnlyDeferredQuery(query, params)
  }

  private[neotypes] def build(): (String, Map[String, QueryParam], List[Int]) = {
    val queryBuilder = new StringBuilder(capacity = 1024)

    @annotation.tailrec
    def loop(
      remaining: List[Part],
      accParams: Map[String, QueryParam],
      accParamLocations: List[Int],
      nextParamIdx: Int
    ): (String, Map[String, QueryParam], List[Int]) =
      remaining match {
        case Nil =>
          (
            queryBuilder.mkString,
            accParams,
            accParamLocations,
          )

        case Query(query, paramLocations) :: xs =>
          val offset = queryBuilder.size
          queryBuilder.append(query)

          val needsSpace = !query.endsWith(" ") && xs.collectFirst {
            case Query(part, _) => !part.startsWith(" ")
            case Param(_) => true
          }.getOrElse(false)
          if(needsSpace) {
            queryBuilder.append(" ")
          }

          loop(
            remaining = xs,
            accParams,
            accParamLocations ::: paramLocations.map(_ + offset),
            nextParamIdx
          )

        case Param(param) :: xs =>
          val paramName = s"${PARAMETER_NAME_PREFIX}${nextParamIdx}"
          val paramLocation = queryBuilder.size
          queryBuilder.append("$")
          queryBuilder.append(paramName)

          loop(
            remaining = xs,
            accParams + (paramName -> param),
            paramLocation :: accParamLocations,
            nextParamIdx + 1
          )

        case SubQueryParam(name, value) :: xs =>
          loop(
            remaining = xs,
            accParams + (name -> value),
            accParamLocations,
            nextParamIdx
          )
      }

    loop(
      remaining = this.parts,
      accParams = Map.empty,
      accParamLocations = List.empty,
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
  final case class Query(part: String, paramLocations: List[Int] = List.empty) extends Part
  final case class Param(value: QueryParam) extends Part
  final case class SubQueryParam(name: String, value: QueryParam) extends Part
}
