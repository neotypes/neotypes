package neotypes

import neotypes.DeferredQueryBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query}
import neotypes.mappers.{ExecutionMapper, ResultMapper}

case class DeferredQuery[T](query: String, params: Map[String, Any] = Map()) {

  def list[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[List[T]] =
    session.transact(tx => list(tx))

  def seq[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[Seq[T]] =
    session.transact(tx => seq(tx))

  def single[F[_]](session: Session[F])(implicit rm: ResultMapper[T]): F[T] =
    session.transact(tx => single(tx))

  def execute[F[_]](session: Session[F])(implicit rm: ExecutionMapper[T]): F[T] =
    session.transact(tx => execute(tx))

  def stream[S[_], F[_]](session: Session[F])(implicit rm: ResultMapper[T], sb: StreamBuilder[S, F], F: Async[F]): S[T] = {
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

  def stream[S[_], F[_]](tx: Transaction[F])(implicit rm: ResultMapper[T], sb: StreamBuilder[S, F]): S[T] =
    tx.stream(query, params)

  def withParams(params: Map[String, Any]): DeferredQuery[T] = copy(params = this.params ++ params)

}

class DeferredQueryBuilder(private[neotypes] val parts: Seq[Part]) {
  protected[neotypes] lazy val rawQuery: String = parts.zipWithIndex.map {
    case (Query(part), _) => part
    case (Param(_), index) => "$" + PARAMETER_NAME_PREFIX + (index / 2 + 1)
  }.mkString("")

  protected[neotypes] lazy val params: Map[String, Any] = parts.collect {
    case Param(value) => value
  }.zipWithIndex.map {
    case (value, index) => PARAMETER_NAME_PREFIX + (index + 1) -> value
  }.toMap

  def this(query: String) = this(Seq(Query(query)))

  def query[T]: DeferredQuery[T] = {
    DeferredQuery(rawQuery, params)
  }

  def +(other: DeferredQueryBuilder): DeferredQueryBuilder = {
    new DeferredQueryBuilder(
      (parts.slice(0, parts.size - 1) :+ parts.last.asInstanceOf[Query].merge(other.parts.head.asInstanceOf[Query])) ++ other.parts.tail
    )
  }

  def +(other: String): DeferredQueryBuilder = {
    new DeferredQueryBuilder(
      parts.slice(0, parts.size - 1) :+ parts.last.asInstanceOf[Query].merge(Query(other))
    )
  }
}

object DeferredQueryBuilder {

  val PARAMETER_NAME_PREFIX = "p"

  sealed trait Part

  case class Query(part: String) extends Part {
    def merge(query: Query) = Query(part + " " + query.part)
  }

  case class Param(value: Any) extends Part

}