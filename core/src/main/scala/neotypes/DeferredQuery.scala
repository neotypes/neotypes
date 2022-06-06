package neotypes

import mappers.{ExecutionMapper, ResultMapper}
import neotypes.DeferredQuery.CollectAsPartiallyApplied
import types.QueryParam

import scala.collection.compat.Factory
import scala.collection.mutable.StringBuilder

sealed trait Deferred[T] {
  def query: String
  def params: Map[String, QueryParam]
  def paramLocations: List[Int]

  def effectDriverTransaction[F[_], O](driver: Driver[F])(tx: Transaction[F] => F[O]): F[O]

  def effectDriverTransactionConfig[F[_], O](driver: Driver[F])(config: TransactionConfig, tx: Transaction[F] => F[O]): F[O]

  def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T]

  def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T]

  def withParams(params: Map[String, QueryParam]): Deferred[T]

  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory -> this)

  final def list[F[_], O](driver: Driver[F])(implicit rm: ResultMapper[T]): F[List[T]] =
    effectDriverTransaction(driver)(tx => list(tx))

  final def list[F[_], C[_]](driver: Driver[F], config: TransactionConfig)
                (implicit rm: ResultMapper[T]): F[List[T]] =
    effectDriverTransactionConfig(driver)(config, tx => list(tx))

  final def set[F[_]](driver: Driver[F])
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    effectDriverTransaction(driver)(tx => set(tx))

  final def set[F[_]](driver: Driver[F], config: TransactionConfig)
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    effectDriverTransactionConfig(driver)(config, tx => set(tx))

  final def vector[F[_]](driver: Driver[F])
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    effectDriverTransaction(driver)(tx => vector(tx))

  final def vector[F[_]](driver: Driver[F], config: TransactionConfig)
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    effectDriverTransactionConfig(driver)(config, tx => vector(tx))

  final def map[F[_], O, K, V](driver: Driver[F])
                        (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    effectDriverTransaction[F, Map[K,V]](driver)(tx => map[F, K, V](tx))

  final def map[F[_], O, K, V](driver: Driver[F], config: TransactionConfig)
                        (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    effectDriverTransactionConfig[F, Map[K, V]](driver)(config, tx => map[F, K, V](tx))

  final def single[F[_]](driver: Driver[F])
                  (implicit rm: ResultMapper[T]): F[T] =
    effectDriverTransaction(driver)(tx => single(tx))

  final def single[F[_]](driver: Driver[F],config: TransactionConfig)
                  (implicit rm: ResultMapper[T]): F[T] =
    effectDriverTransactionConfig(driver)(config, tx => single(tx))

  final def stream[S[_], F[_]](driver: StreamingDriver[S, F])
                        (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransaction[S, F](driver)(tx => stream(tx))

  final def stream[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                        (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransactionConfig[S, F](driver)(config, tx => stream(tx))

  final def execute[F[_]](driver: Driver[F])
                   (implicit em: ExecutionMapper[T]): F[T] =
    effectDriverTransaction(driver)(tx => execute(tx))

  final def execute[F[_]](driver: Driver[F], config: TransactionConfig)
                   (implicit em: ExecutionMapper[T]): F[T] =
    driver.transactReadOnly(config)(tx => execute(tx))

  final def list[F[_]](tx: Transaction[F])
                (implicit rm: ResultMapper[T]): F[List[T]] =
    tx.list(query, params)

  final def map[F[_], K, V](tx: Transaction[F])
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] = {
    internal.utils.void(ev)
    tx.map(query, params)
  }

  final def set[F[_]](tx: Transaction[F])
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    tx.set(query, params)

  final def vector[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    tx.vector(query, params)

  final def single[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  final def execute[F[_]](tx: Transaction[F])
                   (implicit em: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)

  final def stream[S[_], F[_]](tx: StreamingTransaction[S, F])
                        (implicit rm: ResultMapper[T]): S[T] =
    tx.stream(query, params)

  final def withParameterPrefixBase(prefix: String): (String, Map[String, QueryParam], List[Int]) = {
    val newParams = this.params.map {
      case (k, v) =>
        (prefix + k) -> v
    }

    val newLocations = this.paramLocations.sorted.zipWithIndex.map {
      case (location, i) =>
        location + (i * prefix.length)
    }

    val newQuery = newLocations.foldLeft(this.query) {
      case (query, location) =>
        query.patch(location + 1, prefix, 0)
    }

    (
      newQuery,
       newParams,
       newLocations
    )
  }
}

final case class DeferredQuery[T](
     override val query: String,
     override val params: Map[String, QueryParam],
     override val paramLocations: List[Int]) extends Deferred[T] {
  override def effectDriverTransaction[F[_], O](driver: Driver[F])(tx: Transaction[F] => F[O]): F[O] =
    driver.transact(tx)

  override def effectDriverTransactionConfig[F[_], O](driver: Driver[F])(config: TransactionConfig, tx: Transaction[F] => F[O]): F[O] =
    driver.transact(config)(tx)

  override def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransact(st)

  override def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransact(config)(st)

  override def withParams(params: Map[String, QueryParam]): DeferredQuery[T] =
    this.copy(params = this.params ++ params)

  private[neotypes] def withParameterPrefix(prefix: String): DeferredQuery[T] = {
    val (newQuery, newParams, newLocations) = withParameterPrefixBase(prefix)
    DeferredQuery(
      query = newQuery,
      params = newParams,
      paramLocations = newLocations
    )
  }
}

final case class DeferredQueryReadOnly[T](
                              override val query: String,
                              override val params: Map[String, QueryParam],
                              override val paramLocations: List[Int]) extends Deferred[T] {

  def effectDriverTransaction[F[_], O](driver: Driver[F])(tx: Transaction[F] => F[O]): F[O] =
    driver.transactReadOnly(tx)

  override def effectDriverTransactionConfig[F[_], O](driver: Driver[F])(config: TransactionConfig, tx: Transaction[F] => F[O]): F[O] =
    driver.transactReadOnly(config)(tx)

  override def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransactReadOnly(st)

  override def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T] =
    driver.streamingTransactReadOnly(config)(st)

  override def withParams(params: Map[String, QueryParam]): DeferredQueryReadOnly[T] =
    this.copy(params = this.params ++ params)

  private[neotypes] def withParameterPrefix(prefix: String): DeferredQueryReadOnly[T] = {
    val (newQuery, newParams, newLocations) = withParameterPrefixBase(prefix)
    DeferredQueryReadOnly(
      query = newQuery,
      params = newParams,
      paramLocations = newLocations
    )
  }
}

private[neotypes] object DeferredQuery {
  private[neotypes] final class CollectAsPartiallyApplied[T, C](private val factoryAndDq: (Factory[T, C], Deferred[T])) extends AnyVal {
    def apply[F[_]](driver: Driver[F])
                   (implicit rm: ResultMapper[T]): F[C] = {
      val (factory, dq) = factoryAndDq
      driver.transact(tx => tx.collectAs(factory)(dq.query, dq.params))
    }

    def apply[F[_]](driver: Driver[F], config: TransactionConfig)
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
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query, SubQueryParam}

  def query[T <: Any]: DeferredQuery[T] = {
    val (query, params, paramLocations) = baseBuilder()
    DeferredQuery(query, params, paramLocations)
  }

  def queryReadOnly[T <: Any]: DeferredQueryReadOnly[T] = {
    val (query, params, paramLocations) = baseBuilder()
    DeferredQueryReadOnly(query, params, paramLocations)
  }

  def baseBuilder():(String, Map[String, QueryParam], List[Int])  = {
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
