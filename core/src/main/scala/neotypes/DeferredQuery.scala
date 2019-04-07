package neotypes

import neotypes.mappers.{ExecutionMapper, ResultMapper}

import scala.collection.mutable.StringBuilder

final case class DeferredQuery[T](query: String, params: Map[String, Any] = Map.empty) {
  def list[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[List[T]] =
    session.transact(tx => list(tx))

  def seq[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[Seq[T]] =
    session.transact(tx => seq(tx))

  def single[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[T] =
    session.transact(tx => single(tx))

  def execute[F[_]](session: Session[F])(implicit rm: ExecutionMapper[T]): F[T] =
    session.transact(tx => execute(tx))

  def stream[S[_], F[_]](session: Session[F])(implicit rm: ResultMapper[T], sb: Stream[S, F], F: Async[F]): S[T] = {
    val tx = session.beginTransaction()
    sb.fToS(
      F.flatMap(tx) { t =>
        F.success(sb.onComplete(stream(t)) {
          t.rollback()
        })
      }
    )
  }

  def list[F[_]](tx: Transaction[F])(implicit rm: ResultMapper[T]): F[List[T]] =
    tx.list(query, params)

  def seq[F[_]](tx: Transaction[F])(implicit rm: ResultMapper[T]): F[Seq[T]] =
    tx.seq(query, params)

  def single[F[_]](tx: Transaction[F])(implicit rm: ResultMapper[T]): F[T] =
    tx.single(query, params)

  def execute[F[_]](tx: Transaction[F])(implicit rm: ExecutionMapper[T]): F[T] =
    tx.execute(query, params)

  def stream[S[_], F[_]](tx: Transaction[F])(implicit rm: ResultMapper[T], sb: Stream[S, F]): S[T] =
    tx.stream(query, params)

  def withParams(params: Map[String, Any]): DeferredQuery[T] =
    copy(params = this.params ++ params)
}

private[neotypes] class DeferredQueryBuilder(private val parts: List[DeferredQueryBuilder.Part]) {
  import DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query}

  def query[T]: DeferredQuery[T] = {
    @annotation.tailrec
    def loop(remaining: List[Part], queryBuilder: StringBuilder, accParams: Map[String, Any], nextParamIdx: Int): DeferredQuery[T] =
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
        StringBuilder.newBuilder,
        accParams = Map.empty,
        nextParamIdx = 1
      )
  }

  def +(that: DeferredQueryBuilder): DeferredQueryBuilder =
    new DeferredQueryBuilder(
      this.parts ::: that.parts
    )
}

private[neotypes] object DeferredQueryBuilder {
  final val PARAMETER_NAME_PREFIX: String = "p"

  sealed trait Part extends Product with Serializable

  final case class Query(part: String) extends Part

  final case class Param(value: Any) extends Part
}
