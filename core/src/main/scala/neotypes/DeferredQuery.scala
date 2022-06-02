package neotypes

import mappers.{ExecutionMapper, ResultMapper}
import neotypes.DeferredQuery.CollectAsPartiallyApplied
import types.QueryParam

import scala.collection.compat.Factory
import scala.collection.mutable.StringBuilder

abstract class Deferred[T] private[neotypes] {
  import DeferredQuery._
  val query: String
  val params: Map[String, QueryParam]
  val paramLocations: List[Int]

  def driverTransactionFC[F[_], C[_]](driver: Driver[F])(tx: Transaction[F] => F[C[T]]): F[C[T]]

  def driverTransactionConfigFC[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[C[T]])): F[C[T]]

  def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T]

  def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T]

  def driverTransactionF[F[_]](driver: Driver[F])(tx: Transaction[F] => F[T]): F[T]

  def driverTransactionConfigF[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[T])): F[T]

  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C]

  def withParams(params: Map[String, QueryParam]): Deferred[T]

  def list[F[_], C[_]](driver: Driver[F])(implicit rm: ResultMapper[T]): F[List[T]] =
    driverTransactionFC(driver)(tx => list(tx))

  def list[F[_], C[_]](driver: Driver[F], config: TransactionConfig)
                (implicit rm: ResultMapper[T]): F[List[T]] =
    driverTransactionConfigFC(driver)(config, tx => list(tx))

  def set[F[_]](driver: Driver[F])
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    driverTransactionFC(driver)(tx => set(tx))

  def set[F[_]](driver: Driver[F], config: TransactionConfig)
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    driverTransactionConfigFC(driver)(config, tx => set(tx))

  def vector[F[_]](driver: Driver[F])
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    driverTransactionFC(driver)(tx => vector(tx))

  def vector[F[_]](driver: Driver[F], config: TransactionConfig)
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    driverTransactionConfigFC(driver)(config, tx => vector(tx))

  def single[F[_]](driver: Driver[F])
                  (implicit rm: ResultMapper[T]): F[T] =
    driverTransactionF(driver)(tx => single(tx))

  def single[F[_]](driver: Driver[F],config: TransactionConfig)
                  (implicit rm: ResultMapper[T]): F[T] =
    driverTransactionConfigF(driver)(config, tx => single(tx))

  def stream[S[_], F[_]](driver: StreamingDriver[S, F])
                        (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransaction[S, F](driver)(tx => stream(tx))

  def stream[S[_], F[_]](driver: StreamingDriver[S, F], config: TransactionConfig)
                        (implicit rm: ResultMapper[T]): S[T] =
    streamDriverTransactionConfig[S, F](driver)(config, tx => stream(tx))

  def execute[F[_]](driver: Driver[F])
                   (implicit em: ExecutionMapper[T]): F[T] =
    driverTransactionF(driver)(tx => execute(tx))

  def execute[F[_]](driver: Driver[F], config: TransactionConfig)
                   (implicit em: ExecutionMapper[T]): F[T] =
    driver.transactReadOnly(config)(tx => execute(tx))


  def list[F[_]](tx: Transaction[F])
                (implicit rm: ResultMapper[T]): F[List[T]] =
    tx.list(query, params)

  def map[F[_], K, V](tx: Transaction[F])
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] = {
    internal.utils.void(ev)
    tx.map(query, params)
  }

  def set[F[_]](tx: Transaction[F])
               (implicit rm: ResultMapper[T]): F[Set[T]] =
    tx.set(query, params)

  def vector[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[Vector[T]] =
    tx.vector(query, params)


  def single[F[_]](tx: Transaction[F])
                  (implicit rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  def execute[F[_]](tx: Transaction[F])
                   (implicit em: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)


  def stream[S[_], F[_]](tx: StreamingTransaction[S, F])
                        (implicit rm: ResultMapper[T]): S[T] =
    tx.stream(query, params)
}

case class DeferredCommand[T](
     query: String,
     params: Map[String, QueryParam],
     paramLocations: List[Int]) extends Deferred[T] {
  def driverTransactionFC[F[_], C[_]](driver: Driver[F])(tx: Transaction[F] => F[C[T]]): F[C[T]] = {
    driver.transact(tx)
  }
  def driverTransactionConfigFC[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[C[T]])): F[C[T]] = {
    driver.transact(config)(tx)
  }

  def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T] = {
    driver.streamingTransact(st)
  }
  def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T] = {
    driver.streamingTransact(config)(st)
  }

  def driverTransactionF[F[_]](driver: Driver[F])(tx: Transaction[F] => F[T]): F[T] = driver.transact(tx)

  def driverTransactionConfigF[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[T])): F[T] = driver.transact(config)(tx)

  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory -> this)

  def withParams(params: Map[String, QueryParam]): Deferred[T] =
    this.copy(params = this.params ++ params)

  def map[F[_], K, V](driver: Driver[F])
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    driver.transact(tx => map(tx))

  def map[F[_], K, V](driver: Driver[F], config: TransactionConfig)
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    driver.transact(config)(tx => map(tx))

  private[neotypes] def withParameterPrefix(prefix: String): DeferredCommand[T] = {
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

    DeferredCommand(
      query = newQuery,
      params = newParams,
      paramLocations = newLocations
    )
  }
}

case class DeferredQuery[T](
                               query: String,
                               params: Map[String, QueryParam],
                               paramLocations: List[Int]) extends Deferred[T] {
  def driverTransactionFC[F[_], C[_]](driver: Driver[F])(tx: Transaction[F] => F[C[T]]): F[C[T]] = {
    driver.transactReadOnly(tx)
  }
  def driverTransactionConfigFC[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[C[T]])): F[C[T]] = {
    driver.transactReadOnly(config)(tx)
  }

  def streamDriverTransaction[S[_], F[_]](driver: StreamingDriver[S, F])(st: StreamingTransaction[S, F] => S[T]): S[T] = {
    driver.streamingTransactReadOnly(st)
  }
  def streamDriverTransactionConfig[S[_], F[_]](driver: StreamingDriver[S, F])(config: TransactionConfig, st: StreamingTransaction[S, F] => S[T]): S[T] = {
    driver.streamingTransactReadOnly(config)(st)
  }

  def driverTransactionF[F[_]](driver: Driver[F])(tx: Transaction[F] => F[T]): F[T] = driver.transactReadOnly(tx)
  
  def driverTransactionConfigF[F[_], C[_]](driver: Driver[F])(config: TransactionConfig, tx: (Transaction[F] => F[T])): F[T] = driver.transactReadOnly(config)(tx)

  def collectAs[C](factory: Factory[T, C]): CollectAsPartiallyApplied[T, C] =
    new CollectAsPartiallyApplied(factory -> this)

  def withParams(params: Map[String, QueryParam]): Deferred[T] =
    this.copy(params = this.params ++ params)

  def map[F[_], K, V](driver: Driver[F])
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    driver.transactReadOnly(tx => map(tx))

  def map[F[_], K, V](driver: Driver[F], config: TransactionConfig)
                     (implicit ev: T <:< (K, V), rm: ResultMapper[(K, V)]): F[Map[K, V]] =
    driver.transactReadOnly(config)(tx => map(tx))

  private[neotypes] def withParameterPrefix(prefix: String): DeferredQuery[T] = {
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

    DeferredQuery(
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

  def command[T <: Any]: DeferredCommand[T] = {
    basebuilder[T, DeferredCommand[T]]((query, params, paramLocations) => DeferredCommand(query, params, paramLocations))
  }

  def query[T <: Any]: DeferredQuery[T] = {
    basebuilder[T, DeferredQuery[T]]((query, params, paramLocations) => DeferredQuery(query, params, paramLocations))
  }

  def basebuilder[T <: Any, D <: Deferred[T]](f: (String, Map[String, QueryParam], List[Int]) => D): D = {
    val queryBuilder = new StringBuilder(capacity = 1024)
    @annotation.tailrec
    def loop(
              remaining: List[Part],
              accParams: Map[String, QueryParam],
              accParamLocations: List[Int],
              nextParamIdx: Int
            ): D =
      remaining match {
        case Nil =>
          f(
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
