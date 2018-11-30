package neotypes

import neotypes.LazySessionBuilder.{PARAMETER_NAME_PREFIX, Param, Part, Query}
import neotypes.Session.LazySession

class LazySessionBuilder(private[neotypes] val parts: Seq[Part]) {
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

  def query[T]: LazySession[T] = {
    LazySession(rawQuery, params)
  }

  def +(other: LazySessionBuilder): LazySessionBuilder = {
    new LazySessionBuilder(
      (parts.slice(0, parts.size - 1) :+ parts.last.asInstanceOf[Query].merge(other.parts.head.asInstanceOf[Query])) ++ other.parts.tail
    )
  }

  def +(other: String): LazySessionBuilder = {
    new LazySessionBuilder(
      parts.slice(0, parts.size - 1) :+ parts.last.asInstanceOf[Query].merge(Query(other))
    )
  }
}

object LazySessionBuilder {

  val PARAMETER_NAME_PREFIX = "p"

  sealed trait Part

  case class Query(part: String) extends Part {
    def merge(query: Query) = Query(part + " " + query.part)
  }

  case class Param(value: Any) extends Part

}