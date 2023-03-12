package neotypes
package query

import internal.syntax.async._
import internal.syntax.stream._
import mappers.ResultMapper
import model.query.QueryParam

import org.neo4j.driver.summary.ResultSummary

import scala.collection.Factory
import scala.collection.mutable.StringBuilder

sealed trait BaseQuery {
  /** The query statement that will be executed. */
  def query: String

  /** @The parameters that will be substituted into the query statement. */
  def params: Map[String, QueryParam]

  /** Creates a new query with an updated set of parameters.
    *
    * @note If params contains a key that is already present in the current query,
    * the new value one will override the previous one.
    *
    * @param params QueryParams to be added.
    * @return a new query with params added to existing params.
    */
  def withParams(params: Map[String, QueryParam]): BaseQuery

  override final def toString: String =
    s"""${this.getClass.getSimpleName}:
       |query:
       |${query}
       |params:${params.view.map(param => s"\t${param._1}: ${param._2}").mkString("\n")}
     """.stripMargin
}

/** Represents a query that doesn't produce results when executed. */
final class ExecuteQuery private[neotypes] (
    val query: String,
    val params: Map[String, QueryParam]
) extends BaseQuery {
  override def withParams(params: Map[String, QueryParam]): ExecuteQuery =
    new ExecuteQuery(
      query = this.query,
      params = this.params ++ params
    )

  /** Executes the query and returns its [[ResultSummary]].
    *
    * @example
    * {{{
    * val result: F[ResultSummary] =
    *   "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *     .execute
    *     .resultSummary(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def resultSummary[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[ResultSummary] =
    driver.transact(config)(tx => resultSummary(tx))

  /** Executes the query and returns its [[ResultSummary]].
    *
    * @example
    * {{{
    * val result: F[ResultSummary] = driver.transact { tx =>
    *   "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *     .execute
    *     .resultSummary(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def resultSummary[F[_]](tx: Transaction[F]): F[ResultSummary] =
    tx.execute(query, params)

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * val result: F[Unit] =
    *   "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *     .execute
    *     .void(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def void[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[Unit] =
    driver.transact(config)(tx => void(tx))

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * val result: F[Unit] = driver.transact { tx =>
    *   "CREATE (p:Person { name: 'Charlize Theron', born: 1975 })"
    *     .execute
    *     .void(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will execute the query.
    */
  def void[F[_]](tx: Transaction[F]): F[Unit] =
    tx.execute(query, params).void(tx.F)
}

/** Phantom type to determine the appropriate return type of a DeferredQuery. */
sealed trait ResultType {
  type AsyncR[T]
  type StreamR[T]

  private[neotypes] def async[F[_], T](result: F[(T, ResultSummary)])(implicit F: Async[F]): F[AsyncR[T]]
  private[neotypes] def stream[S[_], T](result: S[Either[T, ResultSummary]])(implicit S: Stream[S]): S[StreamR[T]]
}
object ResultType {
  final case object Simple extends ResultType {
    override final type AsyncR[T] = T
    override final type StreamR[T] = T

    override private[neotypes] def async[F[_], T](result: F[(T, ResultSummary)])(implicit F: Async[F]): F[AsyncR[T]] =
      result.map(_._1)

    override private[neotypes] def stream[S[_], T](result: S[Either[T, ResultSummary]])(implicit S: Stream[S]): S[StreamR[T]] =
      result.collect {
        case Left(t) => t
      }
  }

  final case object WithResultSummary extends ResultType {
    override final type AsyncR[T] = (T, ResultSummary)
    override final type StreamR[T] = Either[T, ResultSummary]

    override private[neotypes] def async[F[_], T](result: F[(T, ResultSummary)])(implicit F: Async[F]): F[AsyncR[T]] =
      result

    override private[neotypes] def stream[S[_], T](result: S[Either[T, ResultSummary]])(implicit S: Stream[S]): S[StreamR[T]] =
      result
  }
}

/** Represents a query that produces results when executed.
  *
  * @tparam T the type of the value(s) that will be returned.
  *
  * @see <a href="https://neotypes.github.io/neotypes/parameterized_queries.html">The parametrized queries documentation</a>.
  */
final class DeferredQuery[T, RT <: ResultType] private[neotypes] (
    val query: String,
    val params: Map[String, QueryParam],
    val RT: RT,
    mapper: ResultMapper[T]
) extends BaseQuery {
  override def withParams(params: Map[String, QueryParam]): DeferredQuery[T, RT] =
    new DeferredQuery(
      query = this.query,
      params = this.params ++ params,
      RT = this.RT,
      mapper = this.mapper
    )

  /** Transforms this simple query, into one that preserves the [[ResultSummary]] of its executions. */
  def withResultSummary(
    implicit ev: RT <:< ResultType.Simple.type
  ): DeferredQuery[T, ResultType.WithResultSummary.type] =
    new DeferredQuery(
      query = this.query,
      params = this.params,
      RT = ResultType.WithResultSummary,
      mapper = this.mapper
    )

  /** Executes the query and returns the unique / first value.
    *
    * @example
    * {{{
    * val result: F[(Person, Movie)] =
    *   "MATCH (p: Person {name: 'Charlize Theron'})-[]->(m: Movie) RETURN p, m"
    *     .query(ResultMapper[(Person, Movie)])
    *     .single(driver)
    * }}}
    *
    * @note May fail if the query doesn't return exactly one record.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  def single[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[RT.AsyncR[T]] =
    driver.transact(config)(tx => single(tx))

  /** Executes the query and returns the unique / first value.
    *
    * @example
    * {{{
    * val result: F[(Person, Movie)] = driver.transact { tx =>
    *   "MATCH (p: Person {name: 'Charlize Theron'})-[]->(m: Movie) RETURN p, m"
    *     .query(ResultMapper[(Person, Movie)])
    *     .single(tx)
    * }
    * }}}
    *
    * @note May fail if the query doesn't return exactly one record.
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will compute a single T element.
    */
  def single[F[_]](tx: Transaction[F]): F[RT.AsyncR[T]] =
    RT.async(tx.single(query, params, mapper))(tx.F)

  /** Executes the query and returns all results as a collection of values.
    *
    * @example
    * {{{
    * val result: F[List[Person]] =
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .collectAs(List, driver)
    * }}}
    *
    * @param factory a Scala factory of the collection that will be collected.
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @tparam C collection type.
    * @return An effectual value that will compute a collection of T elements.
    */
  def collectAs[F[_], C](factory: Factory[T, C], driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[RT.AsyncR[C]] =
    driver.transact(config)(tx => collectAs(factory, tx))

  /** Executes the query and returns all results as a collection of values.
    *
    * @example
    * {{{
    * val result: F[List[Person]] = driver.transact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .collectAs(List, tx)
    * }
    * }}}
    *
    * @param factory a Scala factory of the collection that will be collected.
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @tparam C collection type.
    * @return An effectual value that will compute a collection of T elements.
    */
  def collectAs[F[_], C](factory: Factory[T, C], tx: Transaction[F]): F[RT.AsyncR[C]] =
    RT.async(tx.collectAs(query, params, mapper, factory))(tx.F)

  /** Executes the query and returns a [[List]] of values.
    *
    * @example
    * {{{
    * val result: F[List[Person]] =
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .list(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  def list[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[RT.AsyncR[List[T]]] =
    driver.transact(config)(tx => list(tx))

  /** Executes the query and returns a [[List]] of values.
    *
    * @example
    * {{{
    * val result: F[List[Person]] = driver.transact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .list(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will compute a List of T elements.
    */
  def list[F[_]](tx: Transaction[F]): F[RT.AsyncR[List[T]]] =
    collectAs(factory = List, tx)

  /** Executes the query and returns a [[Set]] of values.
    *
    * @example
    * {{{
    * val result: F[Set[Person]] =
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .set(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  def set[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[RT.AsyncR[Set[T]]] =
    driver.transact(config)(tx => set(tx))

  /** Executes the query and returns a [[Set]] of values.
    *
    * @example
    * {{{
    * val result: F[Set[Person]] = driver.transact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .set(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will compute a Set of T elements.
    */
  def set[F[_]](tx: Transaction[F]): F[RT.AsyncR[Set[T]]] =
    collectAs(factory = Set, tx)

  /** Executes the query and returns a [[Vector]] of values.
    *
    * @example
    * {{{
    * val result: F[Vector[Person]] =
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .vector(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  def vector[F[_]](driver: Driver[F], config: TransactionConfig = TransactionConfig.default): F[RT.AsyncR[Vector[T]]] =
    driver.transact(config)(tx => vector(tx))

  /** Executes the query and returns a [[Vector]] of values.
    *
    * @example
    * {{{
    * val result: F[Vector[Person]] = driver.transact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .vector(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @tparam F effect type.
    * @return An effectual value that will compute a Vector of T elements.
    */
  def vector[F[_]](tx: Transaction[F]): F[RT.AsyncR[Vector[T]]] =
    collectAs(factory = Vector, tx)

  /** Executes the query and returns a [[Map]] of values.
    *
    * @example
    * {{{
    * val result: F[Map[String, Person]] =
    *   "MATCH (p: Person) RETURN elementId(p), p"
    *     .query(ResultMapper[(String, Person)])
    *     .map(driver)
    * }}}
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param ev evidence that T is a tuple (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value elements.
    */
  def map[F[_], K, V](driver: Driver[F], config: TransactionConfig = TransactionConfig.default)
                     (implicit ev: T <:< (K, V)): F[RT.AsyncR[Map[K, V]]] =
    driver.transact(config)(tx => map(tx))

  /** Executes the query and returns a [[Map]] of values.
    *
    * @example
    * {{{
    * val result: F[Map[String, Person]] = driver.transact { tx =>
    *   "MATCH (p: Person) RETURN elementId(p), p"
    *     .query(ResultMapper[(String, Person)])
    *     .map(tx)
    * }
    * }}}
    *
    * @param tx neotypes transaction.
    * @param ev evidence that T is a tuple (K, V).
    * @tparam F effect type.
    * @tparam K keys type.
    * @tparam V values type.
    * @return An effectual value that will compute a Map of key-value elements.
    */
  def map[F[_], K, V](tx: Transaction[F])
                     (implicit ev: T <:< (K, V)): F[RT.AsyncR[Map[K, V]]] =
    RT.async(
      tx.collectAs(query, params, mapper.map(ev), factory = Map.mapFactory[K, V])
    )(tx.F)

  /** Executes the query and returns a Stream of values.
    *
    * @example
    * {{{
    * val result: S[F, Person] =
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .stream(streamingDriver)
    * }}}
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param driver neotypes driver.
    * @param config neotypes transaction config.
    * @param chunkSize number of elements to pull each time from the database; by default 256.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T elements.
    */
  def stream[S[_], F[_]](
    driver: StreamingDriver[S, F], config: TransactionConfig = TransactionConfig.default, chunkSize: Int = 256
  ): S[RT.StreamR[T]] =
    driver.streamingTransact(config)(tx => stream(tx, chunkSize))

  /** Executes the query and returns a Stream of values.
    *
    * @example
    * {{{
    * val result: S[F, Person] = streamingDriver.streamingTransact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .stream(tx)
    * }
    * }}}
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param tx neotypes transaction.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T elements.
    */
  def stream[S[_], F[_]](tx: StreamingTransaction[S, F]): S[RT.StreamR[T]] =
    stream(tx, chunkSize = 256)

  /** Executes the query and returns a Stream of values.
    *
    * @example
    * {{{
    * val result: S[F, Person] = streamingDriver.streamingTransact { tx =>
    *   "MATCH (p: Person) RETURN p"
    *     .query(ResultMapper[Person])
    *     .stream(tx)
    * }
    * }}}
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">The streaming documentation</a>.
    *
    * @param tx neotypes transaction.
    * @param chunkSize number of elements to pull each time from the database; by default 256.
    * @tparam S stream type.
    * @tparam F effect type.
    * @return An effectual Stream of T elements.
    */
  def stream[S[_], F[_]](tx: StreamingTransaction[S, F], chunkSize: Int): S[RT.StreamR[T]] =
    RT.stream(tx.stream(query, params, mapper, chunkSize))(tx.S)
}

/** A builder for constructing instance of [[DeferredQuery]].
  *
  * The idiomatic way to use the [[DeferredQueryBuilder]] is with the `c` string interpolator.
  *
  * @see <a href="https://neotypes.github.io/neotypes/parameterized_queries.html">The parametrized queries documentation</a>.
  *
  * @example
  * {{{
  * val name = "John"
  * val born = 1980
  *
  * val query = c"CREATE (: Person { name: \${name}, born: \${born} })"
  * }}}
  */
final class DeferredQueryBuilder private[neotypes] (private val parts: List[DeferredQueryBuilder.Part]) {
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query, SubQueryParam}

  /** Creates a [[ExecuteQuery]]. */
  def execute: ExecuteQuery = {
    val (query, params, _) = build()
    new ExecuteQuery(query, params)
  }

  /** Creates a [[DeferredQuery]] using the provided [[ResultMapper]]. */
  def query[T](mapper: ResultMapper[T]): DeferredQuery[T, ResultType.Simple.type] = {
    val (query, params, _) = build()
    new DeferredQuery(query, params, RT = ResultType.Simple, mapper)
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
          queryBuilder.append("$").append(paramName)
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

  /** Concatenate another [[DeferredQueryBuilder]] with this one.
    *
    * @param that the [[DeferredQueryBuilder]] to be concatenated.
    * @return A new [[DeferredQueryBuilder]].
    */
  def +(that: DeferredQueryBuilder): DeferredQueryBuilder =
    new DeferredQueryBuilder(this.parts ::: that.parts)

  /** Concatenate a [[String]] with this [[DeferredQueryBuilder]].
    *
    * @param that the [[String]] to be concatenated.
    * @return A new [[DeferredQueryBuilder]].
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
