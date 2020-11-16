package neotypes

import generic.Exported
import mappers.ParameterMapper
import types.QueryParam

import scala.reflect.macros.blackbox

final class CypherStringInterpolator(private val sc: StringContext) extends AnyVal {
  def c(args: Any*): DeferredQueryBuilder = macro CypherStringInterpolator.macroImpl
}

object CypherStringInterpolator {

  def createQuery(sc: StringContext)(parameters: QueryArg*): DeferredQueryBuilder = {
    val queries = sc.parts.iterator
    val params = parameters.iterator

    @annotation.tailrec
    def loop(paramNext: Boolean, output: List[DeferredQueryBuilder.Part]): List[DeferredQueryBuilder.Part] =
      if (paramNext && params.hasNext) {
        params.next() match {
          case QueryArg.Param(value)      => loop(paramNext = false, output :+ DeferredQueryBuilder.Param(value))
          case QueryArg.CaseClass(params) => loop(paramNext = false, output ++ caseClassParts(params))
        }
      } else if (queries.hasNext) {
        loop(paramNext = true, output :+ DeferredQueryBuilder.Query(queries.next()))
      } else {
        output
      }

    val queryParts = loop(paramNext = false, Nil)

    new DeferredQueryBuilder(queryParts)
  }

  private val CommaQuery = DeferredQueryBuilder.Query(",")

  private def caseClassParts(params: Map[String, QueryParam]): List[DeferredQueryBuilder.Part] = {

    @scala.inline
    def makeParts(label: String, queryParam: QueryParam, last: Boolean): List[DeferredQueryBuilder.Part] = {
      val query = DeferredQueryBuilder.Query(label + ": ")
      val param = DeferredQueryBuilder.Param(queryParam)

      if (last) List(query, param) else List(query, param, CommaQuery)
    }

    @annotation.tailrec
    def loop(input: List[(String, QueryParam)], output: List[DeferredQueryBuilder.Part]): List[DeferredQueryBuilder.Part] =
      input match {
        case Nil => output
        case (label, param) :: Nil => output ++ makeParts(label, param, last = true)
        case (label, param) :: tail => loop(tail, output ++ makeParts(label, param, last = false))
      }

    loop(params.toList, Nil)
  }

  def macroImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DeferredQueryBuilder] = {
    import c.universe._

    val q"$foo($sc)" = c.prefix.tree
    neotypes.internal.utils.void(q"$foo")

    val parameters = args.map { arg =>
      val nextElement = arg.tree
      val tpe = nextElement.tpe.widen

      q"_root_.neotypes.QueryArgMapper[${tpe}].toArg(${nextElement})"
    }

    c.Expr(
      q"_root_.neotypes.CypherStringInterpolator.createQuery(${sc})(..${parameters})"
    )
  }
}

sealed trait QueryArg

object QueryArg {
  final case class Param(value: QueryParam) extends QueryArg
  final case class CaseClass(params: Map[String, QueryParam]) extends QueryArg
}

@annotation.implicitNotFound("Could not find the QueryArgMapper for ${A}.")
trait QueryArgMapper[A] {
  def toArg(value: A): QueryArg
}

object QueryArgMapper extends QueryOrgMappersLowPriority {

  def apply[A](implicit ev: QueryArgMapper[A]): QueryArgMapper[A] = ev

  implicit def fromParameterMapper[A: ParameterMapper]: QueryArgMapper[A] =
    (a: A) => QueryArg.Param(ParameterMapper[A].toQueryParam(a))

}

trait QueryOrgMappersLowPriority {
  implicit def fromCaseClassArgMapper[A: CaseClassArgMapper]: QueryArgMapper[A] =
    CaseClassArgMapper[A]
}

@annotation.implicitNotFound(
"""
Could not find the CaseClassArgMapper for ${A}.

Import `neotypes.generic.auto._` for the automated derivation, or use the semiauto one:
`implicit val instance: CaseClassArgMapper[A] = neotypes.generic.semiauto.deriveCaseClassArgMapper`
"""
)
trait CaseClassArgMapper[A] extends QueryArgMapper[A] {
  def toArg(value: A): QueryArg.CaseClass
}

object CaseClassArgMapper extends CaseClassArgMappersLowPriority {
  def apply[A](implicit ev: CaseClassArgMapper[A]): CaseClassArgMapper[A] = ev
}

trait CaseClassArgMappersLowPriority {
  implicit final def exportedCaseClassArgMapper[A](implicit exported: Exported[CaseClassArgMapper[A]]): CaseClassArgMapper[A] =
    exported.instance
}
