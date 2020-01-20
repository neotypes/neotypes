package neotypes

import types.QueryParam

import scala.reflect.macros.blackbox

final class CypherStringInterpolator(private val sc: StringContext) extends AnyVal {
  def c(args: Any*): DeferredQueryBuilder = macro CypherStringInterpolator.macroImpl
}

object CypherStringInterpolator {
  def createQuery(sc: StringContext)(parameters: QueryParam*): DeferredQueryBuilder = {
    val queries = sc.parts.iterator.map(DeferredQueryBuilder.Query)
    val params = parameters.iterator.map(DeferredQueryBuilder.Param)

    val queryParts = new Iterator[DeferredQueryBuilder.Part] {
      private var paramNext: Boolean = false
      override def hasNext: Boolean = queries.hasNext
      override def next(): DeferredQueryBuilder.Part =
        if (paramNext && params.hasNext) {
          paramNext = false
          params.next()
        } else {
          paramNext = true
          queries.next()
        }
    }

    new DeferredQueryBuilder(queryParts.toList)
  }

  def macroImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[DeferredQueryBuilder] = {
    import c.universe._

    val q"$foo($sc)" = c.prefix.tree
    neotypes.internal.utils.void(q"$foo")

    val parameters = args.map { arg =>
      val nextElement = arg.tree
      val tpe = nextElement.tpe.widen

      q"neotypes.mappers.ParameterMapper[${tpe}].toQueryParam(${nextElement})"
    }

    c.Expr(
      q"neotypes.CypherStringInterpolator.createQuery(${sc})(..${parameters})"
    )
  }
}
