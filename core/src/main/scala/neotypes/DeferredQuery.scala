package neotypes

import internal.syntax.async._
import mappers.{ExecutionMapper, ResultMapper}
import types.QueryParam

import scala.collection.compat.Factory
import scala.collection.mutable.StringBuilder
import scala.language.higherKinds

/** Representation of a query that is deferred in an effect type and evaluated asynchronously in a session transaction backed by neo4j.
  *
  * @param query statement that will be executed
  * @param params variable values substituted into the query statement
  * @tparam T the type of the value that will be returned when the query is executed.
  */
final case class DeferredQuery[T] private[neotypes] (query: String, params: Map[String, QueryParam]) {
  import DeferredQuery.{CollectAsPartiallyApplied, StreamPartiallyApplied}

  /** Executes the query and returns a custom collection of values.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[ListMap[Person, Movie]] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].collectAs(ListMap)(s)
    * }}}
    *
    * @param factory the type of the custom collection to return.
    * @tparam C collection type constructed
    * @return an asynchronous C[T] in F
    */
  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory, this)

  /** Executes the query and returns a List of values.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[List[(Person, Movie)]] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].list(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return List[T] in effect type F
    */
  def list[F[_]](session: Session[F])
                (implicit F: Async[F], rm: ResultMapper[T]): F[List[T]] =
    session.transact(tx => list(tx))

  /** Executes the query and returns a Map[K,V] of values.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[Map[Person, Movie]] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].map(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param ev Map (Key, Value) type constraint.  T must be subtype of (K, V)
    * @param F effect type F
    * @param rm result mapper for type (K, V)
    * @tparam F effect type
    * @tparam K key for Map
    * @tparam V value for Map
    * @return Map[K, V] in effect type F
    */
  def map[F[_], K, V](session: Session[F])
                     (implicit ev: T <:< (K, V), F: Async[F], rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    session.transact(tx => map(tx))

  /** Executes the query and returns a Set of values.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[Set[(Person, Movie)]] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].set(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return Set[T] in effect type F
    */
  def set[F[_]](session: Session[F])
               (implicit F: Async[F], rm: ResultMapper[T]): F[Set[T]] =
    session.transact(tx => set(tx))

  /** Executes the query and returns a Vector of values.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[Vector[(Person, Movie)]] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].vector(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return Vector[T] in effect type F
    */
  def vector[F[_]](session: Session[F])
                  (implicit F: Async[F], rm: ResultMapper[T]): F[Vector[T]] =
    session.transact(tx => vector(tx))

  /** Executes the query and returns the first record in the result
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * val result: F[(Person, Movie)] =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)].single(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return F[T]
    */
  def single[F[_]](session: Session[F])
                  (implicit F: Async[F], rm: ResultMapper[T]): F[T] =
    session.transact(tx => single(tx))

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * val s: Session[F] = ??? //define session
    * c"CREATE (chat: Chat { user1: ${"Charlize"}, user2: ${"Theron"}, messages: ${messages} })"
    *   .query[Unit].execute(s)
    * }}}
    *
    * @param session neotypes session for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return F[T]
    */
  def execute[F[_]](session: Session[F])
                   (implicit F: Async[F], rm: ExecutionMapper[T]): F[T] =
    session.transact(tx => execute(tx))

  /** Executes the query and returns a List of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)]
    * val result: F[List[(Person, Movie)]] =
    *   session.transact(tx => deferredQuery.list(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return List[T] in effect type F
    */
  def list[F[_]](tx: Transaction[F])
                (implicit F: Async[F], rm: ResultMapper[T]): F[List[T]] =
    tx.list(query, params)

  /** Executes the query and returns a Map[K,V] of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)]
    * val result: F[Map[Person, Movie]] =
    *   session.transact(tx => deferredQuery.map(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param ev  Map (Key, Value) type constraint.  T must be subtype of (K, V)
    * @param F effect type F
    * @param rm result mapper for type (K, V)
    * @tparam F effect type
    * @tparam K key for Map
    * @tparam V value for Map
    * @return Map[K, V] in effect type F
    */
  def map[F[_], K, V](tx: Transaction[F])
                     (implicit ev: T <:< (K, V), F: Async[F], rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    tx.map(query, params)

  /** Executes the query and returns a Set of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)]
    * val result: F[Set[(Person, Movie)]] =
    *   session.transact(tx => deferredQuery.set(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return Set[T] in effect type F
    */
  def set[F[_]](tx: Transaction[F])
               (implicit F: Async[F], rm: ResultMapper[T]): F[Set[T]] =
    tx.set(query, params)

  /** Executes the query and returns a Vector of values.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)]
    * val result: F[Vector[(Person, Movie)]] =
    *   session.transact(tx => deferredQuery.vector(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return Vector[T] in effect Type F
    */
  def vector[F[_]](tx: Transaction[F])
                  (implicit F: Async[F], rm: ResultMapper[T]): F[Vector[T]] =
    tx.vector(query, params)

  /** Executes the query and returns the first record in the result
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"MATCH (p:Person {name: 'Charlize Theron'})-[]->(m:Movie) RETURN p,m"
    *   .query[(Person, Movie)]
    * val result: F[(Person, Movie)] =
    *   session.transact(tx => deferredQuery.single(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return F[T]
    */
  def single[F[_]](tx: Transaction[F])
                  (implicit F: Async[F], rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  /** Executes the query and ignores its output.
    *
    * @example
    * {{{
    * val deferredQuery =
    *   c"CREATE (chat: Chat { user1: ${"Charlize"}, user2: ${"Theron"}, messages: ${messages} })".query[Unit]
    *   session.transact(tx => deferredQuery.execute(tx))
    * }}}
    *
    * @param tx neotypes transaction for effect type F
    * @param F effect type F
    * @param rm result mapper for type T
    * @tparam F effect type
    * @return F[T]
    */
  def execute[F[_]](tx: Transaction[F])
                   (implicit F: Async[F], rm: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)

  /** Evaluate the query on a stream
    *
    * @see <a href="https://neotypes.github.io/neotypes/docs/streams.html">stream documentation</a>
    *
    * @tparam S stream type
    * @return S[T]
    */
  def stream[S[_]]: StreamPartiallyApplied[S, T] =
    new StreamPartiallyApplied(this)

  /** Creates a new query with an updated set of parameters.
    * @note Map concatenation can cause values in the second Map to override values in the first Map that share the same Key
    *
    * @param params QueryParams to  be concatenated
    * @return DeferredQuery with params concatenated to existing params
    */
  def withParams(params: Map[String, QueryParam]): DeferredQuery[T] =
    this.copy(params = this.params ++ params)
}

private[neotypes] object DeferredQuery {
  private[neotypes] final class CollectAsPartiallyApplied[T, C](private val factory: Factory[T, C], private val dq: DeferredQuery[T]) {
    def apply[F[_]](session: Session[F])
                   (implicit F: Async[F], rm: ResultMapper[T]): F[C] =
      session.transact(tx => tx.collectAs(factory)(dq.query, dq.params))

    def apply[F[_]](tx: Transaction[F])
                   (implicit F: Async[F], rm: ResultMapper[T]): F[C] =
      tx.collectAs(factory)(dq.query, dq.params)
  }

  private[neotypes] final class StreamPartiallyApplied[S[_], T](private val dq: DeferredQuery[T]) extends AnyVal {
    def apply[F[_]](session: Session[F])(implicit F: Async[F], rm: ResultMapper[T], S: Stream.Aux[S, F]): S[T] =
      S.fToS(
        session.transaction.flatMap { tx =>
          F.delay(
            S.onComplete(tx.stream(dq.query, dq.params))(tx.rollback)
          )
        }
      )

    def apply[F[_]](tx: Transaction[F])(implicit F: Async[F], rm: ResultMapper[T], S: Stream.Aux[S, F]): S[T] =
      tx.stream(dq.query, dq.params)
  }
}

/** Builder that constructs a DeferredQuery
  * <p>
  * The idiomatic way to use the DeferredQueryBuilder is with String Interpolation
  * @example
  * {{{
  *   val stringInter = c"""create (a:Test {name: $name,""" + c"born: $born})"
  *   val deferredQuery = stringInter.query[Unit]
  * }}}
  *
  * @param parts
  */
final class DeferredQueryBuilder private[neotypes] (private val parts: List[DeferredQueryBuilder.Part]) {
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query}

  /** Creates a DeferredQuery from this builder
    *
    * @tparam T the result type of the constructed query
    * @return DeferredQuery[T]
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

  /** Concatenate Query and Param of the this DeferredQueryBuilder with supplied DeferredQueryBuilder
    *
    * @param that DeferredQueryBuilder to be concatenated
    * @return DeferredQueryBuilder result
    */
  def +(that: DeferredQueryBuilder): DeferredQueryBuilder =
    new DeferredQueryBuilder(this.parts ::: that.parts)

  /** Concatenate the String element as a Query into this DeferredQueryBuilder
    *
    * @param that String element to be added as a Query
    * @return DeferredQueryBuilder result
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
